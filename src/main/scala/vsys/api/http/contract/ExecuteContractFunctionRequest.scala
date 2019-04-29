package vsys.api.http.contract

import io.swagger.annotations.ApiModelProperty
import play.api.libs.json.{Format, Json}


case class ExecuteContractFunctionRequest(@ApiModelProperty(value = "Base58 encoded sender public key", required = true)
                                          sender: String,
                                          @ApiModelProperty(value = "Base58 encoded contract id", required = true)
                                          contractId: String,
                                          @ApiModelProperty(required = true)
                                          funcIdx: Short,
                                          @ApiModelProperty(value = "Base58 encoded dataStack", required = true)
                                          data: String,
                                          @ApiModelProperty(value = "Base58 encoded description of contract")
                                          description: Option[String],
                                          @ApiModelProperty(required = true)
                                          fee: Long,
                                          @ApiModelProperty(required = true)
                                          feeScale: Short)

object ExecuteContractFunctionRequest {
  implicit  val executeContractFunctionRequestFormat: Format[ExecuteContractFunctionRequest] = Json.format
}