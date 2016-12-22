package scorex.transaction

import com.google.common.primitives.{Bytes, Ints, Longs}
import play.api.libs.json.{JsObject, Json}
import scorex.account.Account
import scorex.crypto.encode.Base58
import scorex.crypto.hash.FastCryptographicHash._
import scorex.serialization.Deser
import scorex.transaction.TypedTransaction._

import scala.util.{Failure, Try}
import scala.concurrent.duration._


case class GenesisTransaction(recipient: Account,
                              amount: Long,
                              timestamp: Long,
                              signature: Array[Byte]
                             )  extends TypedTransaction {

  import scorex.transaction.GenesisTransaction._

  override val assetFee: (Option[AssetId], Long) = (None, 0)
  override val id: Array[Byte] = signature

  protected def jsonBase() = {
    Json.obj("type" -> transactionType.id,
      "id" -> Base58.encode(id),
      "fee" -> 0,
      "timestamp" -> timestamp,
      "signature" -> Base58.encode(this.signature)
    )
  }

  lazy val deadline = timestamp + 24.hours.toMillis

  val transactionType = TransactionType.GenesisTransaction

  lazy val creator: Option[Account] = None

  lazy val json: JsObject =
    jsonBase() ++ Json.obj("recipient" -> recipient.address, "amount" -> amount)

  lazy val bytes: Array[Byte] = {
    val typeBytes = Array(TransactionType.GenesisTransaction.id.toByte)

    val timestampBytes = Bytes.ensureCapacity(Longs.toByteArray(timestamp), TimestampLength, 0)

    val amountBytes = Bytes.ensureCapacity(Longs.toByteArray(amount), AmountLength, 0)

    val rcpBytes = recipient.bytes
    require(rcpBytes.length == Account.AddressLength)

    val res = Bytes.concat(typeBytes, timestampBytes, rcpBytes, amountBytes)
    require(res.length == dataLength)
    res
  }

  val dataLength = TypeLength + BASE_LENGTH

  lazy val signatureValid: Boolean = {
    val typeBytes = Array(TransactionType.GenesisTransaction.id.toByte)
    val timestampBytes = Bytes.ensureCapacity(Longs.toByteArray(timestamp), TimestampLength, 0)
    val amountBytes = Bytes.ensureCapacity(Longs.toByteArray(amount), AmountLength, 0)
    val data = Bytes.concat(typeBytes, timestampBytes, recipient.bytes, amountBytes)

    val h = hash(data)
    Bytes.concat(h, h).sameElements(signature)
  }

  def validate: ValidationResult.Value =
    if (amount < 0) {
      ValidationResult.NegativeAmount
    } else if (!Account.isValid(recipient)) {
      ValidationResult.InvalidAddress
    } else ValidationResult.ValidateOke


  override def balanceChanges(): Seq[BalanceChange] = Seq(BalanceChange(AssetAcc(recipient, None), amount))
}


object GenesisTransaction extends Deser[GenesisTransaction] {

  private val RECIPIENT_LENGTH = Account.AddressLength
  private val BASE_LENGTH = TimestampLength + RECIPIENT_LENGTH + AmountLength

  def generateSignature(recipient: Account, amount: Long, timestamp: Long): Array[Byte] = {
    val typeBytes = Bytes.ensureCapacity(Ints.toByteArray(TransactionType.GenesisTransaction.id), TypeLength, 0)
    val timestampBytes = Bytes.ensureCapacity(Longs.toByteArray(timestamp), TimestampLength, 0)
    val amountBytes = Longs.toByteArray(amount)
    val amountFill = new Array[Byte](AmountLength - amountBytes.length)

    val data = Bytes.concat(typeBytes, timestampBytes, recipient.bytes, Bytes.concat(amountFill, amountBytes))

    val h = hash(data)
    Bytes.concat(h, h)
  }

  def parseBytes(data: Array[Byte]): Try[GenesisTransaction] = {
    data.head match {
      case transactionType: Byte if transactionType == TransactionType.GenesisTransaction.id =>
        parseTail(data.tail)
      case transactionType =>
        Failure(new Exception(s"Incorrect transaction type '$transactionType' in GenesisTransaction data"))
    }
  }

  def parseTail(data: Array[Byte]): Try[GenesisTransaction] = Try {
    require(data.length >= BASE_LENGTH, "Data does not match base length")

    var position = 0

    val timestampBytes = java.util.Arrays.copyOfRange(data, position, position + TimestampLength)
    val timestamp = Longs.fromByteArray(timestampBytes)
    position += TimestampLength

    val recipientBytes = java.util.Arrays.copyOfRange(data, position, position + RECIPIENT_LENGTH)
    val recipient = new Account(Base58.encode(recipientBytes))
    position += RECIPIENT_LENGTH

    val amountBytes = java.util.Arrays.copyOfRange(data, position, position + AmountLength)
    val amount = Longs.fromByteArray(amountBytes)

    GenesisTransaction(recipient, amount, timestamp, GenesisTransaction.generateSignature(recipient, amount, timestamp))
  }

  def create(recipient: Account,
             amount: Long,
             timestamp: Long) : GenesisTransaction = {
    val signature = GenesisTransaction.generateSignature(recipient, amount, timestamp)
    GenesisTransaction(recipient, amount, timestamp, signature)
  }
}
