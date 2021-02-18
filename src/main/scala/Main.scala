import dao.UserDAO._
import model._
import slick.model.Column

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn.readLine
import scala.util.{Failure, Success}

import service.MessageService

object Main extends App {

//  val db = Database.forConfig("mysqlDB")
//
//  // Create table
//  val usersTable: TableQuery[Users] = TableQuery[Users]
//
//  // DROP and CREATE TABLE
//  val dropUsers = DBIO.seq(usersTable.schema.drop)
//  val initUsers = DBIO.seq(usersTable.schema.create)
//
//  db.run(dropUsers)
//  db.run(initUsers)

  // Lifted Embedding
  //  - define data types to store row data
  //  - define Table objects representing mappings between our data types and the database
  //  - define TableQueries and combinators to build useful queries before we run them against the database

  //resetTableData()

  val messageService = MessageService(slick.jdbc.MySQLProfile)

  resetAndLoadTableData().onComplete {
    case Success(value) => messageService.initMessages()
    case Failure(e) => messageService.initMessages()
  }

  systemRunner()


  def systemRunner(): Unit = {
    println("WELCOME, ENTER A COMMAND: ")
    val input: String = readLine()
    helper(input)

    @tailrec
    def helper(input: String): Unit = {
      input.toUpperCase() match {
        case "R" | "READ" => {
          println("Enter ID: ")
          val id: String = readLine()

          id.toIntOption match {
            case Some(i: Int) => {
              read(i) andThen {
                case Success(value) => println(value)
                case Failure(exception) => println("Could not find user...")
              }
            }
            case None => println(s"User not found with ID: $id")
          }
        }
        case "M" | "MESSAGES" => messageService.read().onComplete {
          case Success(value) => value.foreach(println)
          case Failure(exception) => println("something went wrong")
        }
        case "L" | "LIST" => listAll().onComplete(println(_))
        //case "C" | "CREATE" => create(getUserFromInput())
        case "Q" | "QUIT" => println("INITIATING SHUTDOWN")
        case _ => println("INVALID INPUT")
      }

      input.toUpperCase match {
        case "Q" | "QUIT" => println("SHUTDOWN COMPLETE")
        case _ => {
          println("ENTER A COMMAND: ")
          val input = readLine()
          helper(input)
        }
      }
    }
  }

}
