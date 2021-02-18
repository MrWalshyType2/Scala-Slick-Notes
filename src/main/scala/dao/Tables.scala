package dao

trait Tables { self: Profile =>

  import profile.api._

  case class Message(content: String, senderFk: Long, id: Long = 0L)

  final class MessageTable(tag: Tag) extends Table[Message](tag, "messages") {

    // Columns & (Name, Constraints*)
    // def name = column[DATA-TYPE]("COLUMN_NAME", column_settings*)
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def content = column[String]("CONTENT")
    def senderFk = column[Long]("SENDERFK")

    // 'default projection' for mapping between columns in the table and
    // instances of the case class.
    //  - mapTo creates a two-way mapping between the fields in User and the database columns in UserTable
    //  def * = (id, fName, lName, age) <>(User.tupled, User.unapply)
    def * = (content, senderFk, id).mapTo[Message]
  }

  object messages extends TableQuery(new MessageTable(_)) {
    def messagesFrom(id: Long) = this.filter(_.id ===id)
    val numOfMessages = this.distinct.size
  }
}
