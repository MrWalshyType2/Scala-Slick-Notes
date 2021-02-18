package util

import org.scalatest._
import org.scalatest.flatspec._
import org.scalatest.matchers._

abstract class UnitSpec extends AnyFlatSpec with should.Matchers with
  OptionValues with Inside with Inspectors
