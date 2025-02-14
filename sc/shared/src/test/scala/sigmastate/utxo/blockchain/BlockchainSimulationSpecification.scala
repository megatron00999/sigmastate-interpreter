package sigmastate.utxo.blockchain

import java.io.{FileWriter, File}

import sigmastate.CompilerCrossVersionProps
import sigmastate.Values.{TrueLeaf, GetVarBoolean}
import sigmastate.helpers.{ContextEnrichingTestProvingInterpreter, ErgoLikeTestProvingInterpreter}
import sigmastate.interpreter.ContextExtension
import sigmastate.utxo.blockchain.BlockchainSimulationTestingCommons._

import scala.collection.concurrent.TrieMap


class BlockchainSimulationSpecification extends BlockchainSimulationTestingCommons
  with CompilerCrossVersionProps {

  implicit lazy val IR = new TestingIRContext

  import ValidationState._

  property("apply one valid block") {
    val state = ValidationState.initialState(activatedVersionInTests, initBlock(ergoTreeVersionInTests))
    val miner = new ErgoLikeTestProvingInterpreter()
    val block = generateBlock(state, miner, 0)
    val updStateTry = state.applyBlock(block)
    updStateTry.isSuccess shouldBe true
  }

  property("too costly block") {
    val state = ValidationState.initialState(activatedVersionInTests, initBlock(ergoTreeVersionInTests))
    val miner = new ErgoLikeTestProvingInterpreter()
    val block = generateBlock(state, miner, 0)
    val updStateTry = state.applyBlock(block, maxCost = 1)
    updStateTry.isSuccess shouldBe false
  }

  property("apply many blocks") {
    val state = ValidationState.initialState(activatedVersionInTests, initBlock(ergoTreeVersionInTests))
    val miner = new ErgoLikeTestProvingInterpreter()
    checkState(state, miner, 0, randomDeepness)
  }

  property("apply many blocks with enriched context") {
    val state = ValidationState.initialState(activatedVersionInTests, initBlock(ergoTreeVersionInTests))
    val miner = new ErgoLikeTestProvingInterpreter()
    val varId = 1.toByte
    val prop = GetVarBoolean(varId).get.toSigmaProp
    // unable to spend boxes without correct context extension
    an[RuntimeException] should be thrownBy {
      checkState(state, miner, 0, randomDeepness, Some(mkTestErgoTree(prop)))
    }

    // spend boxes with context extension
    val contextExtension = ContextExtension(Map(varId -> TrueLeaf))
    checkState(state, miner, 0, randomDeepness, Some(mkTestErgoTree(prop)), contextExtension)
  }

}