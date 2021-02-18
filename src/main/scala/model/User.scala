package model

//import slick.lifted.Tag
//import slick.model.Table

import slick.jdbc.MySQLProfile.api._

case class User(id: Int, fName: String, lName: String, age: Int)

case class SafeUser(fName: String, lName: String, age: Int)

// the Tag allows Slick to automatically manage multiple uses of the table in a single query
case class UserTable(tag: Tag) extends Table[User](tag, "users") {

  // Columns & (Name, Constraints*)
  // def name = column[DATA-TYPE]("COLUMN_NAME", column_settings*)
  def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
  def fName = column[String]("FNAME")
  def lName = column[String]("LNAME")
  def age = column[Int]("AGE")

  // 'default projection' for mapping between columns in the table and
  // instances of the case class.
  //  - mapTo creates a two-way mapping between the fields in User and the database columns in UserTable
//  def * = (id, fName, lName, age) <>(User.tupled, User.unapply)
  def * = (id, fName, lName, age).mapTo[User]
}
