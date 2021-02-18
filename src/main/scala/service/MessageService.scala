package service

import dao.DatabaseLayer
import slick.jdbc.JdbcProfile

import scala.concurrent.Future

class MessageService(private val profileToPass: JdbcProfile) extends DatabaseLayer(profileToPass) {

  import profile.api._

  val db = Database.forConfig("mysqlDB")

  val initData = Seq(
    Message("Hello", 34L),
    Message("Bye", 24L)
  )

  val dropMessageTable: DBIO[Unit] = DBIO.seq(messages.schema.drop)
  val initMessageTable: DBIO[Unit] = DBIO.seq(messages.schema.create)
  val initialDataInsert: DBIO[Unit] = DBIO.seq(messages ++= initData)

  def initMessages(): Future[Option[Int]] = {
    val dropMessagesTable: DBIO[Unit] = DBIO.seq(messages.schema.dropIfExists)
    val createMessagesTable: DBIO[Unit] = DBIO.seq(messages.schema.createIfNotExists)
    val initialDataInsert: DBIO[Option[Int]] = messages ++= initData

    val combined = (dropMessagesTable andThen createMessagesTable andThen initialDataInsert).transactionally

    db.run(combined)
  }

  def read(): Future[Seq[Message]] = {
    db.run(messages.result)
  }
}

object MessageService {

  def apply(profileToPass: JdbcProfile): MessageService = {
    new MessageService(profileToPass)
  }

}
