package sigmastate.serialization

import org.ergoplatform.validation.ValidationSpecification
import org.scalacheck.Gen
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.Assertion
import org.scalacheck.Arbitrary._
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.{ScalaCheckDrivenPropertyChecks, ScalaCheckPropertyChecks}
import sigmastate.Values._
import sigmastate.SType
import sigmastate.helpers.NegativeTesting
import sigmastate.serialization.generators._

trait SerializationSpecification extends AnyPropSpec
  with ScalaCheckPropertyChecks
  with ScalaCheckDrivenPropertyChecks
  with TableDrivenPropertyChecks
  with Matchers
  with ObjectGenerators
  with ConcreteCollectionGenerators
  with OpcodesGen
  with TransformerGenerators
  with RelationGenerators
  with ValidationSpecification
  with NegativeTesting {

  protected def roundTripTest[V <: Value[_ <: SType]](v: V): Assertion = {
    val bytes = ValueSerializer.serialize(v)
    predefinedBytesTest(v, bytes)
    predefinedBytesTestNotFomZeroElement(bytes, v)
  }

  protected def predefinedBytesTest[V <: Value[_ <: SType]](v: V, bytes: Array[Byte]): Assertion = {
    ValueSerializer.serialize(v) shouldEqual bytes
    val r = SigmaSerializer.startReader(bytes)
    val positionLimitBefore = r.positionLimit
    val dv = ValueSerializer.deserialize(r)
    dv shouldEqual v
    r.positionLimit shouldBe positionLimitBefore
  }

  //check that pos and consumed are being implented correctly
  protected def predefinedBytesTestNotFomZeroElement[V <: Value[_ <: SType]](bytes: Array[Byte], v: V): Assertion = {
    val randomInt = Gen.chooseNum(1, 20).sample.get
    val randomBytes = Gen.listOfN(randomInt, arbByte.arbitrary).sample.get.toArray
    val parsedVal = ValueSerializer.deserialize(randomBytes ++ bytes, randomInt)
    parsedVal shouldEqual v
  }
}
