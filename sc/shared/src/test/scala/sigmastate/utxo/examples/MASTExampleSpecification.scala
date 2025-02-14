package sigmastate.utxo.examples

import org.ergoplatform._
import scorex.crypto.authds.avltree.batch.{BatchAVLProver, Insert, Lookup}
import scorex.crypto.authds.{ADKey, ADValue}
import scorex.crypto.hash.{Blake2b256, Digest32}
import sigmastate.SCollection.SByteArray
import sigmastate.Values._
import sigmastate._
import sigmastate.eval.Extensions.ArrayOps
import sigmastate.helpers.{CompilerTestingCommons, ContextEnrichingTestProvingInterpreter, ErgoLikeContextTesting, ErgoLikeTestInterpreter}
import sigmastate.helpers.TestingHelpers._
import sigmastate.lang.Terms._
import sigmastate.interpreter.Interpreter._
import sigmastate.serialization.ValueSerializer
import sigmastate.utxo._
import sigmastate.eval._

import scala.util.Random

/**
  * Example of Merklized Abstract Syntax Tree (MAST).
  * MAST are a proposed addition to Bitcoin that allows for smaller transaction sizes, more privacy, and larger smart
  * contracts. In this case script creator build Merkle tree on top of all script branches, and put script like
  * `provide a correct script and it's path to predefined tree root hash` into blockchain. When someone spend coins,
  * it provide a script, a Merkle path to script and required additional data. Thus, unsued branches of a script
  * remain unrevealed, providing more privacy and saving space in a blockchain.
  * See more at https://bitcointechtalk.com/what-is-a-bitcoin-merklized-abstract-syntax-tree-mast-33fdf2da5e2f
  */
class MASTExampleSpecification extends CompilerTestingCommons
  with CompilerCrossVersionProps {
  implicit lazy val IR: TestingIRContext = new TestingIRContext
  private val reg1 = ErgoBox.nonMandatoryRegisters.head

  /**
    * In the provided example simple branching by condition, based on number of inputs
    */
  property("Merklized Abstract Syntax Tree - simple branching") {
    val scriptId = 21.toByte
    val scriptIsCorrect = DeserializeContext(scriptId, SBoolean)
    val scriptHash = CalcBlake2b256(GetVarByteArray(scriptId).get)
    val script1Bytes = ValueSerializer.serialize(TrueLeaf)
    val script1Hash = Blake2b256(script1Bytes)
    val script2Hash = Blake2b256(ValueSerializer.serialize(GT(SizeOf(Inputs).upcastTo(SLong), LongConstant(1))))

    val prop = mkTestErgoTree(AND(
      scriptIsCorrect,
      If(
        EQ(SizeOf(Inputs), 1),
        EQ(scriptHash, script1Hash),
        EQ(scriptHash, script2Hash))).toSigmaProp)


    val input1 = testBox(20, prop, 0)
    val tx = UnsignedErgoLikeTransaction(IndexedSeq(input1).map(i => new UnsignedInput(i.id)),
      IndexedSeq(testBox(1, TrueTree, 0)))
    val ctx = ErgoLikeContextTesting(
      currentHeight = 50,
      lastBlockUtxoRoot = AvlTreeData.dummy,
      minerPubkey = ErgoLikeContextTesting.dummyPubkey,
      boxesToSpend = IndexedSeq(input1),
      tx,
      self = input1, activatedVersionInTests)


    val prover = new ContextEnrichingTestProvingInterpreter()
      .withContextExtender(scriptId, ByteArrayConstant(script1Bytes))

    val proveEnv = emptyEnv + (ScriptNameProp -> "simple_branching_prove")
    val proof = prover.prove(proveEnv, prop, ctx, fakeMessage).get

    val verifyEnv = emptyEnv + (ScriptNameProp -> "simple_branching_verify")
    (new ErgoLikeTestInterpreter).verify(verifyEnv, prop, ctx, proof, fakeMessage).get._1 shouldBe true
  }

  /**
    * In the provided example there are 5 different branches of a tree, each one require to reveal some secret.
    */
  property("Merklized Abstract Syntax Tree") {
    val scriptId = 21.toByte
    val proofId = 22.toByte
    val secretId = 23.toByte

    val allSecrets = (0 until 5).map(_ => Random.nextString(32).getBytes("UTF-8"))
    val scriptBranches = allSecrets.map(s => EQ(ByteArrayConstant(s), GetVarByteArray(secretId).get))
    val scriptBranchesBytes = scriptBranches.map(b => ValueSerializer.serialize(b))
    val treeElements: Seq[(ADKey, ADValue)] = scriptBranchesBytes.map(s => (ADKey @@@ Blake2b256(s), ADValue @@ s))
    val knownSecretTreeKey = treeElements.head._1
    val knownSecret = ByteArrayConstant(allSecrets.head)

    val avlProver = new BatchAVLProver[Digest32, Blake2b256.type](keyLength = 32, None)
    treeElements.foreach(s => avlProver.performOneOperation(Insert(s._1, s._2)))
    avlProver.generateProof()
    val treeData = SigmaDsl.avlTree(new AvlTreeData(avlProver.digest.toColl, AvlTreeFlags.ReadOnly, 32, None))

    val merklePathToScript = OptionIsDefined(
      IR.builder.mkMethodCall(
        ExtractRegisterAs[SAvlTree.type](Self, reg1).get,
        SAvlTree.getMethod,
        IndexedSeq(
          CalcBlake2b256(GetVarByteArray(scriptId).get),
          GetVarByteArray(proofId).get)).asOption[SByteArray]
    )
    val scriptIsCorrect = DeserializeContext(scriptId, SBoolean)
    val prop = mkTestErgoTree(AND(merklePathToScript, scriptIsCorrect).toSigmaProp)

    val recipientProposition = new ContextEnrichingTestProvingInterpreter().dlogSecrets.head.publicImage
    val selfBox = testBox(20, TrueTree, 0,
      additionalTokens = Seq(),
      additionalRegisters = Map(reg1 -> AvlTreeConstant(treeData)))
    val ctx = ErgoLikeContextTesting(
      currentHeight = 50,
      lastBlockUtxoRoot = AvlTreeData.dummy,
      minerPubkey = ErgoLikeContextTesting.dummyPubkey,
      boxesToSpend = IndexedSeq(selfBox),
      createTransaction(testBox(1, recipientProposition, 0)),
      self = selfBox, activatedVersionInTests)

    avlProver.performOneOperation(Lookup(knownSecretTreeKey))
    val knownSecretPathProof = avlProver.generateProof()
    val usedBranch = scriptBranchesBytes.head
    val prover = new ContextEnrichingTestProvingInterpreter()
      .withContextExtender(secretId, knownSecret)
      .withContextExtender(scriptId, ByteArrayConstant(usedBranch))
      .withContextExtender(proofId, ByteArrayConstant(knownSecretPathProof))

    val proveEnv = emptyEnv + (ScriptNameProp -> "MAST_prove")
    val proof = prover.prove(proveEnv, prop, ctx, fakeMessage).get

    val verifyEnv = emptyEnv + (ScriptNameProp -> "MAST_verify")
    (new ErgoLikeTestInterpreter).verify(verifyEnv, prop, ctx, proof, fakeMessage).get._1 shouldBe true
  }
}
