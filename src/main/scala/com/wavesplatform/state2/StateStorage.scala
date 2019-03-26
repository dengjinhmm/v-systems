package com.wavesplatform.state2

import java.io.File

import com.google.common.primitives.Ints
import com.wavesplatform.utils._
import org.h2.mvstore.MVMap
import scorex.account.Address
import scorex.utils.LogMVMapBuilder

import scala.util.Try

class StateStorage private(file: Option[File]) extends AutoCloseable {

  import StateStorage._

  private val db = createMVStore(file)

  private val variables: MVMap[String, Int] = db.openMap("variables")

  private def setPersistedVersion(version: Int) = variables.put(stateVersion, version)

  private def persistedVersion: Option[Int] = Option(variables.get(stateVersion))

  def getHeight: Int = variables.get(heightKey)

  def setHeight(i: Int): Unit = variables.put(heightKey, i)

  val addressList: MVMap[Int, String] = db.openMap("addressList")

  val addressToID: MVMap[String, Int] = db.openMap("addressToID")

  def setSlotAddress(i: Int, add: String): Unit = addressList.put(i,add)

  def setAddressSlot(add: String, i: Int): Unit = addressToID.put(add,i)

  def getSlotAddress(i: Int): Option[String] = Option(addressList.get(i))

  def getAddressSlot(add: String): Option[Int] = Option(addressToID.get(add))

  def releaseSlotAddress(i: Int): Unit = addressList.remove(i)

  def releaseAddressSlot(add: String): Unit = addressToID.remove(add)

  def getEffectiveSlotAddressSize: Int = addressList.size()

  val transactions: MVMap[ByteStr, (Int, Array[Byte])] = db.openMap("txs", new LogMVMapBuilder[ByteStr, (Int, Array[Byte])]
    .keyType(DataTypes.byteStr).valueType(DataTypes.transactions))

  val portfolios: MVMap[ByteStr, (Long, (Long, Long), Map[Array[Byte], Long])] = db.openMap("portfolios",
    new LogMVMapBuilder[ByteStr, (Long, (Long, Long), Map[Array[Byte], Long])]
      .keyType(DataTypes.byteStr).valueType(DataTypes.portfolios))

  val assets: MVMap[ByteStr, (Boolean, Long)] = db.openMap("assets",
    new LogMVMapBuilder[ByteStr, (Boolean, Long)].keyType(DataTypes.byteStr).valueType(DataTypes.assets))

  val accountTransactionIds: MVMap[AccountIdxKey, ByteStr] = db.openMap("accountTransactionIds",
    new LogMVMapBuilder[AccountIdxKey, ByteStr].valueType(DataTypes.byteStr))

  val accountTransactionsLengths: MVMap[ByteStr, Int] = db.openMap("accountTransactionsLengths",
    new LogMVMapBuilder[ByteStr, Int].keyType(DataTypes.byteStr))

  val balanceSnapshots: MVMap[AccountIdxKey, (Int, Long, Long, Long)] = db.openMap("balanceSnapshots",
    new LogMVMapBuilder[AccountIdxKey, (Int, Long, Long, Long)].valueType(DataTypes.balanceSnapshots))

  val aliasToAddress: MVMap[String, ByteStr] = db.openMap("aliasToAddress", new LogMVMapBuilder[String, ByteStr]
    .valueType(DataTypes.byteStr))

  val orderFills: MVMap[ByteStr, (Long, Long)] = db.openMap("orderFills", new LogMVMapBuilder[ByteStr, (Long, Long)]
    .keyType(DataTypes.byteStr).valueType(DataTypes.orderFills))

  val leaseState: MVMap[ByteStr, Boolean] = db.openMap("leaseState", new LogMVMapBuilder[ByteStr, Boolean]
    .keyType(DataTypes.byteStr))

  val lastBalanceSnapshotHeight: MVMap[ByteStr, Int] = db.openMap("lastUpdateHeight", new LogMVMapBuilder[ByteStr, Int]
    .keyType(DataTypes.byteStr))

  val lastBalanceSnapshotWeightedBalance: MVMap[ByteStr, Long] = db.openMap("lastUpdateWeightedBalance", new LogMVMapBuilder[ByteStr, Long]
    .keyType(DataTypes.byteStr))

  val contracts: MVMap[String, (Boolean, ByteStr, String)] = db.openMap("contracts", new LogMVMapBuilder[String, (Boolean, ByteStr, String)])

  // only support Entry.bytes, in case later we want to support different types and not sure how to serialize here?
  val dbEntries: MVMap[ByteStr, ByteStr] = db.openMap("dbEntries", new LogMVMapBuilder[ByteStr, ByteStr]
    .keyType(DataTypes.byteStr).valueType(DataTypes.byteStr))

  def commit(): Unit = {
     db.commit()
    db.compact(CompactFillRate, CompactMemorySize)
  }

  override def close(): Unit = db.close()
}

object StateStorage {
  private val Version = 1

  private val CompactFillRate = 80
  private val CompactMemorySize = 19 * 1024 * 1024

  private val heightKey = "height"
  private val stateVersion = "stateVersion"

  private def validateVersion(ss: StateStorage): Boolean =
    ss.persistedVersion match {
      case None =>
        ss.setPersistedVersion(Version)
        ss.commit()
        true
      case Some(v) => v == Version

    }

  def apply(file: Option[File], dropExisting: Boolean): Try[StateStorage] =
    createWithStore(file, new StateStorage(file), validateVersion, dropExisting)

  type AccountIdxKey = Array[Byte]

  def accountIndexKey(acc: Address, index: Int): AccountIdxKey = acc.bytes.arr ++ Ints.toByteArray(index)
}
