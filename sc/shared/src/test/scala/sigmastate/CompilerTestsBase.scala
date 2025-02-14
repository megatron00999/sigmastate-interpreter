package sigmastate

import scala.util.DynamicVariable
import sigmastate.lang.{TransformingSigmaBuilder, CompilerResult, CompilerSettings, SigmaCompiler}
import sigmastate.interpreter.Interpreter.ScriptEnv
import sigmastate.Values.{SigmaPropValue, SValue, Value, ErgoTree}
import org.ergoplatform.ErgoAddressEncoder.TestnetNetworkPrefix
import sigmastate.serialization.ValueSerializer
import sigmastate.eval.IRContext
import sigmastate.lang.Terms.ValueOps

trait CompilerTestsBase extends TestsBase {
  protected val _lowerMethodCalls = new DynamicVariable[Boolean](true)

  /** Returns true if MethodCall nodes should be lowered by TypeChecker to the
    * corresponding ErgoTree nodes. E.g. xs.map(f) -->  MapCollection(xs, f).
    * NOTE: The value of the flag is assigned dynamically using _lowerMethodCalls
    * DynamicVariable. */
  def lowerMethodCallsInTests: Boolean = _lowerMethodCalls.value

  /** If true, then all suite properties are executed with _lowerMethodCalls set to false.
    * This allow to test execution of MethodCall nodes in ErgoTree.
    */
  val okRunTestsWithoutMCLowering: Boolean = false

  /** Compiler settings used in tests. */
  val defaultCompilerSettings: CompilerSettings = CompilerSettings(
    TestnetNetworkPrefix, TransformingSigmaBuilder,
    lowerMethodCalls = true
  )

  def compilerSettingsInTests: CompilerSettings =
    defaultCompilerSettings.copy(lowerMethodCalls = lowerMethodCallsInTests)

  def compiler = SigmaCompiler(compilerSettingsInTests)

  def checkSerializationRoundTrip(v: SValue): Unit = {
    val compiledTreeBytes = ValueSerializer.serialize(v)
    withClue(s"(De)Serialization roundtrip failed for the tree:") {
      ValueSerializer.deserialize(compiledTreeBytes) shouldEqual v
    }
  }

  /** Compile the given code to ErgoTree expression. */
  def compile(env: ScriptEnv, code: String)(implicit IR: IRContext): Value[SType] = {
    val res = compiler.compile(env, code)
    checkCompilerResult(res)
    res.buildTree
  }

  /** Check the given [[CompilerResult]] meets equality and sanity requirements. */
  def checkCompilerResult[Ctx <: IRContext](res: CompilerResult[Ctx]): Unit = {
    checkSerializationRoundTrip(res.buildTree)
  }


  /** Compiles the given code and checks the resulting `prop` against `expected`. */
  def compileAndCheck(env: ScriptEnv, code: String, expected: SValue)
      (implicit IR: IRContext): (ErgoTree, SigmaPropValue) = {
    val prop = compile(env, code).asSigmaProp
    prop shouldBe expected
    val tree = mkTestErgoTree(prop)
    (tree, prop)
  }
}
