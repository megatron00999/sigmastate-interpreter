package sigmastate.utxo

import org.ergoplatform.{ErgoBox, ErgoBoxCandidate, ErgoLikeTransaction}
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Assertion, Matchers, PropSpec}
import sigmastate.serialization.Serializer
import sigmastate.serialization.generators.ValueGenerators

class SerializationRoundTripSpec extends PropSpec
  with GeneratorDrivenPropertyChecks
  with Matchers
  with ValueGenerators {

  private def roundTripTest[T](v: T)(implicit serializer: Serializer[T, T]): Assertion = {
    val bytes = serializer.toBytes(v)
    serializer.parseBytes(bytes).get shouldBe v
  }

  private def roundTripTestWithPos[T](v: T)(implicit serializer: Serializer[T, T]): Assertion = {
    val randomBytesCount = Gen.chooseNum(1, 20).sample.get
    val randomBytes = Gen.listOfN(randomBytesCount, arbByte.arbitrary).sample.get.toArray
    val bytes = serializer.toBytes(v)
    serializer.parseBytes(bytes).get shouldBe v
    serializer.parseBody(randomBytes ++ bytes, randomBytesCount) shouldEqual (v, bytes.length)
  }

  property("ErgoBoxCandidate: Serializer round trip") {
    forAll { t: ErgoBoxCandidate => roundTripTest(t)(ErgoBoxCandidate.serializer) }
    forAll { t: ErgoBoxCandidate => roundTripTestWithPos(t)(ErgoBoxCandidate.serializer) }
  }

  property("ErgoBox: Serializer round trip") {
    forAll { t: ErgoBox => roundTripTest(t)(ErgoBox.serializer) }
    forAll { t: ErgoBox => roundTripTestWithPos(t)(ErgoBox.serializer) }
  }

  property("ErgoTransaction: Serializer round trip") {
    forAll { t: ErgoLikeTransaction => roundTripTest(t)(ErgoLikeTransaction.serializer) }
    forAll { t: ErgoLikeTransaction => roundTripTestWithPos(t)(ErgoLikeTransaction.serializer) }
  }
}
