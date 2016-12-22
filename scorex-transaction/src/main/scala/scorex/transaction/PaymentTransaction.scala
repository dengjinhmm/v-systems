package scorex.transaction

import java.util

import com.google.common.primitives.{Bytes, Ints, Longs}
import play.api.libs.json.{JsObject, Json}
import scorex.account.{Account, PrivateKeyAccount, PublicKeyAccount}
import scorex.crypto.EllipticCurveImpl
import scorex.crypto.encode.Base58
import scorex.serialization.Deser
import scorex.transaction.TypedTransaction._
import scala.concurrent.duration._
import scala.util.{Failure, Try}

@deprecated("Use TransferTransaction")
@SerialVersionUID(-4989881425715590828L)
case class PaymentTransaction(sender: PublicKeyAccount, recipient: Account, amount: Long, fee: Long, timestamp: Long, signature: Array[Byte])
    extends TypedTransaction {
  import PaymentTransaction._

  override val transactionType = TransactionType.PaymentTransaction

  override val assetFee: (Option[AssetId], Long) = (None, fee)
  override val id: Array[Byte]                   = signature

  lazy val deadline = timestamp + 24.hours.toMillis

  lazy val hasMinimumFee = fee >= MinimumFee

  protected def jsonBase() = {
    Json.obj("type"      -> transactionType.id,
             "id"        -> Base58.encode(id),
             "fee"       -> fee,
             "timestamp" -> timestamp,
             "signature" -> Base58.encode(this.signature))
  }


  lazy val dataLength = TypeLength + BaseLength

  lazy val creator = Some(sender)

  override lazy val json: JsObject = jsonBase() ++ Json.obj(
      "sender"          -> sender.address,
      "senderPublicKey" -> Base58.encode(sender.publicKey),
      "recipient"       -> recipient.address,
      "amount"          -> amount
    )

  override lazy val bytes: Array[Byte] = {
    val timestampBytes = Longs.toByteArray(timestamp)
    val amountBytes    = Longs.toByteArray(amount)
    val feeBytes       = Longs.toByteArray(fee)

    Bytes.concat(Array(transactionType.id.toByte), timestampBytes, sender.publicKey, recipient.bytes, amountBytes, feeBytes, signature)
  }

  lazy val signatureValid: Boolean = {
    val data = signatureData(sender, recipient, amount, fee, timestamp)
    EllipticCurveImpl.verify(signature, data, sender.publicKey)
  }

  def validate: ValidationResult.Value =
    if (!Account.isValid(recipient)) {
      ValidationResult.InvalidAddress //CHECK IF RECIPIENT IS VALID ADDRESS
    } else if (amount <= 0) {
      ValidationResult.NegativeAmount //CHECK IF AMOUNT IS POSITIVE
    } else if (fee <= 0) {
      ValidationResult.InsufficientFee //CHECK IF FEE IS POSITIVE
    } else if (Try(Math.addExact(amount, fee)).isFailure) {
      ValidationResult.OverflowError // CHECK THAT fee+amount won't overflow Long
    } else if (!signatureValid) {
      ValidationResult.InvalidSignature
    } else ValidationResult.ValidateOke

  override def balanceChanges(): Seq[BalanceChange] =
    Seq(BalanceChange(AssetAcc(sender, None), -amount - fee), BalanceChange(AssetAcc(recipient, None), amount))

  override def equals(other: Any): Boolean = other match {
    case o: PaymentTransaction => super.equals(other) && timestamp == o.timestamp
    case _                     => false
  }

  override def hashCode(): Int = super.hashCode() + timestamp.hashCode()

}

object PaymentTransaction extends Deser[PaymentTransaction] {

  val MinimumFee = 1
  val RecipientLength = Account.AddressLength

  private val SenderLength    = 32
  private val FeeLength       = 8
  private val SignatureLength = 64
  private val BaseLength      = TimestampLength + SenderLength + RecipientLength + AmountLength + FeeLength + SignatureLength

  def apply(sender: PrivateKeyAccount, recipient: Account, amount: Long, fee: Long, timestamp: Long): PaymentTransaction = {
    val sig = generateSignature(sender, recipient, amount, fee, timestamp)
    PaymentTransaction(sender, recipient, amount, fee, timestamp, sig)
  }

  def parseBytes(data: Array[Byte]): Try[PaymentTransaction] = {
    data.head match {
      case transactionType: Byte if transactionType == TransactionType.PaymentTransaction.id =>
        parseTail(data.tail)
      case transactionType =>
        Failure(new Exception(s"Incorrect transaction type '$transactionType' in PaymentTransaction data"))
    }
  }

  def parseTail(data: Array[Byte]): Try[PaymentTransaction] = Try {
    require(data.length >= BaseLength, "Data does not match base length")

    var position = 0

    //READ TIMESTAMP
    val timestampBytes = data.take(TimestampLength)
    val timestamp      = Longs.fromByteArray(timestampBytes)
    position += TimestampLength

    //READ SENDER
    val senderBytes = util.Arrays.copyOfRange(data, position, position + SenderLength)
    val sender      = new PublicKeyAccount(senderBytes)
    position += SenderLength

    //READ RECIPIENT
    val recipientBytes = util.Arrays.copyOfRange(data, position, position + RecipientLength)
    val recipient      = new Account(Base58.encode(recipientBytes))
    position += RecipientLength

    //READ AMOUNT
    val amountBytes = util.Arrays.copyOfRange(data, position, position + AmountLength)
    val amount      = Longs.fromByteArray(amountBytes)
    position += AmountLength

    //READ FEE
    val feeBytes = util.Arrays.copyOfRange(data, position, position + FeeLength)
    val fee      = Longs.fromByteArray(feeBytes)
    position += FeeLength

    //READ SIGNATURE
    val signatureBytes = util.Arrays.copyOfRange(data, position, position + SignatureLength)

    PaymentTransaction(sender, recipient, amount, fee, timestamp, signatureBytes)
  }

  def generateSignature(sender: PrivateKeyAccount, recipient: Account, amount: Long, fee: Long, timestamp: Long): Array[Byte] = {
    EllipticCurveImpl.sign(sender, signatureData(sender, recipient, amount, fee, timestamp))
  }

  private def signatureData(sender: PublicKeyAccount, recipient: Account, amount: Long, fee: Long, timestamp: Long): Array[Byte] = {
    val typeBytes      = Ints.toByteArray(TransactionType.PaymentTransaction.id)
    val timestampBytes = Longs.toByteArray(timestamp)
    val amountBytes    = Longs.toByteArray(amount)
    val feeBytes       = Longs.toByteArray(fee)

    Bytes.concat(typeBytes, timestampBytes, sender.publicKey, recipient.bytes, amountBytes, feeBytes)
  }
}
