package vsys.state.opcdiffs

import com.wavesplatform.state2._
import scorex.transaction.ValidationError
import scorex.transaction.ValidationError.{ContractInvalidOPCode, ContractInvalidStateVariable}
import scorex.account.Address
import vsys.contract.{DataEntry, DataType, ExecutionContext}
import vsys.contract.Contract.checkStateVar

import scala.util.{Left, Right}

object CDBVOpcDiff {

  def set(context: ExecutionContext)(stateVar: Array[Byte],
                                     value: DataEntry): Either[ValidationError, OpcDiff] = {
    if (!checkStateVar(stateVar, value.dataType)) {
      Left(ContractInvalidStateVariable)
    } else {
      if (value.dataType == DataType.Address) {
        val a = Address.fromBytes(value.data).toOption.get
        Right(OpcDiff(relatedAddress = Map(a -> true),
          contractDB = Map(ByteStr(context.contractId.bytes.arr ++ Array(stateVar(0))) -> value.bytes)))
      } else {
        Right(OpcDiff(contractDB = Map(ByteStr(context.contractId.bytes.arr
          ++ Array(stateVar(0))) -> value.bytes)))
      }
    }
  }

  object CDBVType extends Enumeration {
    val SetCDBV = Value(1)
  }

  def parseBytes(context: ExecutionContext)
                (bytes: Array[Byte], data: Seq[DataEntry]): Either[ValidationError, OpcDiff] = bytes.head match {
    case opcType: Byte if opcType == CDBVType.SetCDBV.id && bytes.length == 3 && bytes(1) < context.stateVar.length
      && bytes.last < data.length && bytes.tail.min >= 0 => set(context)(context.stateVar(bytes(1)), data(bytes(2)))
    case _ => Left(ContractInvalidOPCode)
  }

}
