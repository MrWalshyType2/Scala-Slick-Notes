package model

import util.UnitSpec

class UserFlatSpecBDD extends UnitSpec {

  "A SafeUser" should "have a fName set to 'Bob', " +
    "lName set to 'Doro' and age set to '43'" in {
    val user: SafeUser = SafeUser("Bob", "Doro", 43)

    assert(user.fName.contentEquals("Bob"))
    assert(user.lName.contentEquals("Doro"))
    assert(user.age.equals(43))
  }

  it should "produce NoSuchElementException when head is invoked" in {
    assertThrows[NoSuchElementException] {
      Set.empty.head
    }
  }
}
