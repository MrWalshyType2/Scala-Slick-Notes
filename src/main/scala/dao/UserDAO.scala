package dao

import model._
import slick.jdbc.MySQLProfile

import scala.util.Try

// Each Database Management System (DBS) has its own suupport for different data types, dialects of SQL and querying capabilities.
// - Choose the api for the correct DB for your use-case (database-specific profile)
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object UserDAO {

  val db = Database.forConfig("mysqlDB")

  // db.run()
  // - results in a Future[T] (T is the type of the result returned by the DB)

  // Create table query (SELECT *)
  // - TableQuery[UserTable] = Rep(TableExpansion)
  // - Append 'filter' to create a (SELECT * WHERE)
  val usersTable: TableQuery[UserTable] = TableQuery[UserTable]

  def initialData = Seq(
    User(1, "Bob", "Boins", 35),
    User(2, "Elijah", "Darl", 23),
    User(3, "Suzie", "Smith", 32),
    User(4, "Jules", "Del", 17)
  )

  // DROP and CREATE TABLE
  // println(usersTable.schema.createStatements.mkString)
  // - actions are ran against the database
  // - Anything ran against a database is an object representing a DB action that completes with a result of type Unit when ran.
  //   DBIO[T] or DBIOAction
  val dropUserTable: DBIO[Unit] = DBIO.seq(usersTable.schema.drop)
  val initUsersTable: DBIO[Unit] = DBIO.seq(usersTable.schema.create)

  // ++= accepts a sequence of User objects here and then translates them to a bulk INSERT query
  val initialDataInsert: DBIO[Option[Int]] = usersTable ++= initialData

  // The type Query is a monad
  //  - map, flatMap, filter, withFilter (compatible with for comprehensions)
  val forCompExample = for {
    user <- usersTable if user.fName equals "Bob"
  } yield user

  // The type DBIOAction/DBIO[T] is also a monad
  val exampleMultipleActions = dropUserTable andThen // also '>>'
                               initUsersTable andThen
                               initialDataInsert

  //val someSelectQueryAsDBIOAction = usersTable.result
  //db.run(someSelectQueryAsDBIOAction) // turns DBIOAction into a Future[T] where T is the type of the result

  def resetAndLoadTableData(): Future[Option[Int]] = {
    db.run(exampleMultipleActions)
  }

  def resetTableData(): Unit = {
    val dropFuture = Future {
      db.run(dropUserTable)
    }

    dropFuture.onComplete {
      case Success(_) => {
        println("Table dropped successfully")
        db.run(initUsersTable)
        db.run(initialDataInsert)
      }
      case Failure(error) => {
        println("Error dropping DB")
        db.run(initUsersTable)
        db.run(initialDataInsert)
      }
    }
  }

  def create(user: User): Future[String] = {
    db.run(usersTable += user)
      .map(response => "User successfully added")
      .recover {
        case exception: Exception => exception.getCause.getMessage
      }
  }

  def read(id: Int): Future[Option[User]] = {
    db.run(usersTable.filter(_.id === id).result.headOption)
  }

  def readAlt(id: Int, p: String): Future[Option[User]] = {

    // UserTable is a Table
    // usersTable is a TableQuery[UserTable] is a Query[UserTable, User, ?]
    db.run(usersTable.filter { userTable: UserTable =>
      // This is a Rep[Boolean] which evaluates to an SQL statement userTable.id = id
      userTable.id === id
    }.result.headOption)
  }

  def listAll(): Future[Seq[User]] = {
    db.run(usersTable.result)
  }

  def listAllNames(): Future[Seq[String]] = {
    // The map method can be used on a Query to select specific columns
    //  - changes the Mixed and Unpacked types of the Query
    //    Query[ConstColumn[String], String, Seq]
    //  - ConstColumn[T] is a descendant of Rep[T]
    //    fName and lName columns are only passed when the query is filtered or mapped over

    // In a series of transformations and operations, map should be the last called as changing the mixed-type
    // can complicate query composition.
    val query: Query[ConstColumn[String], String, Seq] = usersTable.map(u => s"${u.fName} ${u.lName}")
    db.run(query.result)
  }

  // Mapping a set of columns to a different data structure
  def listAllUsersNoId(): Future[Seq[SafeUser]] = {
    val query = usersTable.map(u => (u.fName, u.lName, u.age).mapTo[SafeUser])
    db.run(query.result)

    // can also select column expressions
    // usersTable.map(u => u.id * 1000L).result.statements.mkString
    //  - select id * 1000 from users
  }

  // returns 0 or 1 dependant on deletion success (1 = success, 0 false)
  def delete(id: Int): Future[Int] = {
    db.run(usersTable.filter(_.id === id).delete)
  }

  def update(user: User): Future[String] = {
    db.run(usersTable.insertOrUpdate(user))
      .map(response => "User successfully updated")
      .recover {
        case ex: Exception => ex.getCause.getMessage
      }
  }

  // Converting a Query to an Action
  // - typically done by calling the result method on the query
  // - an Action represents a sequence of queries
  //
  // Action Type Signature
  // DBIOAction[R, S, E]
  // - R is the type of data expected from the database (User)
  // - S indicates whether the results are streamed (Streaming[T], NoStream)
  // - E is the effect type and will be inferred
  //
  // The Action Simplification
  // DBIO[T] = DBIOAction[T, NoStream, Effect.All]
  //
  // What is an Effect?
  // Broadly speaking, an Effect annotates an action. They can be single or combined:
  // - Read
  // - Read with Transactional
  //
  // Effect's defined in Slick:
  // - Read for queries reading from a database
  // - Write for queries that write or cause an effect that writes on a database.
  // - Schema for schema effects
  // - Transactional for transactional effects
  // - All for all of the above
  //
  // Custom effect types can be added by extending the above
  // trait CustomEffect extends Effect with Read
  //
  // Executing Actions
  // - db.run() runs the Action and returns the result as a single collection, this is known as a materialized result.
  // - db.streams() runs the Action and returns the result as a Stream, this allows large datasets to be incrementally
  //   processed without taking up large amounts of memory.
  //
  // db.run() returns a Future of the final result of an Action
  // - Default Execution context for Future: import scala.concurrent.ExecutionContext.Implicits.global
  //
  // db.stream() returns a DatabasePublisher object instead of a Future, this exposes three methods for interaction
  // with the stream:
  // - subscribe for integration with Akka
  // - mapResult creates a new Publisher which will map a supplied function on the result set from the original publisher
  // - foreach for performing side-effects with results
  // db.stream(userTable.result).foreach(println)


  // Column Expressions
  // filter and map require the building of expressions based on columns defined in a Table.
  //
  // Rep can be used to represent expressions as well as individual columns
  // - Find extension methods for building expressions in ExtensionMethods.scala in Slicks code
  //
  // Equality (===), Inequality (=!=)
  // - Operate on any Rep and produce a Rep[Boolean]
  //
  // String Concatenation (++) equivalent to SQL (||)
  // Pattern matching (like) method
//  val someQueryRep: Query[Rep[String], String, Seq] = usersTable.map(u => u.fName ++ " > " ++ u.lName)
//
//
//  // String Methods
//  // col1 or col2 must be String or Option[String], Operand and result types interpreted as params to Rep[_]
//
//  //       Scala             Result Type    SQL Equivalent
//  //  ===========================================================
//  //       col1.length     	 Int	          char_length(col1)
//  //       col1 ++ col2	     String	        col1 || col2
//  //       c1 like c2      	 Boolean	      c1 like c2
//  //       c1 startsWith c2	 Boolean	      c1 like (c2 || '%')
//  //       c1 endsWith c2  	 Boolean	      c1 like ('%' || c2)
//  //       c1.toUpperCase	   String	        upper(c1)
//  //       c1.toLowerCase  	 String	        lower(c1)
//  //       col1.trim	       String	        trim(col1)
//  //       col1.ltrim	       String	        ltrim(col1)
//  //       col1.rtrim	       String	        rtrim(col1)
//
//  // Numeric methods
//  // - Operate on Rep with values of types: Ints, Longs, Doubles, Floats, Shorts, Bytes, and BigDecimals.
//  // - Operand and Result types should be interpreted as parameters to Rep[_]
//  //  Scala           Operand Column Types  Result type   SQL Equivalent
//  //  ==================================================================
//  //  col1 + col2	    A or Option[A] 	      A	            col1 + col2
//  //  col1 - col2	    A or Option[A]	      A	            col1 - col2
//  //  col1 * col2	    A or Option[A]	      A	            col1 * col2
//  //  col1 / col2	    A or Option[A]	      A	            col1 / col2
//  //  col1 % col2	    A or Option[A]	      A	            mod(col1, col2)
//  //  col1.abs	      A or Option[A]	      A	            abs(col1)
//  //  col1.ceil	      A or Option[A]	      A	            ceil(col1)
//  //  col1.floor	    A or Option[A]	      A	            floor(col1)
//  //  col1.round	    A or Option[A]	      A	            round(col1, 0)
//
//
//  // Boolean methods
//  // - Operate on a boolean Rep
//  //
//  // Scala          Operand Column Types          Result type       SQL Equivalent
//  // ==============================================================================
//  // col1 && col2	  Boolean or Option[Boolean]	  Boolean	          col1 and col2
//  // col1 || col2	  Boolean or Option[Boolean]	  Boolean	          col1 or col2
//  // !col1	        Boolean or Option[Boolean]	  Boolean	          not col1
//
//
//  // Date and Time Methods
//  // - Slick provides column mappings for: Instant, LocalDate, LocalTime,
//  //   LocalDateTime, OffsetTime, OffsetDateTime, and ZonedDateTime
//  // - No special methods for 'java.time' types
//  // - Can use '===', '>=', etc...
//  //
//  // Scala Type	            H2 Column Type	PostgreSQL	  MySQL
//  // ==========================================================
//  // Instant	              TIMESTAMP	      TIMESTAMP	    TEXT
//  // LocalDate	            DATE	          DATE	        DATE
//  // LocalTime	            VARCHAR	        TIME	        TEXT
//  // LocalDateTime	        TIMESTAMP	      TIMESTAMP	    TEXT
//  // OffsetTime	            VARCHAR	        TIMETZ	      TEXT
//  // OffsetDateTime	        VARCHAR	        VARCHAR	      TEXT
//  // ZonedDateTime	        VARCHAR	        VARCHAR	      TEXT
//
//
//  // Slick models nullable columns in SQL as a Rep with Option types
//  // - nullable columns are defined as optionals in a Table class
//  //
//  // def nickname = column[Option[String]]("nickname")
//  //
//  // Slick type-checks column expressions to make sure the types match, otherwise it throws a type error.
//  // - Slick is able to handle optional and non-optional column comparisons as long as the operands
//  //   are of type A and Option[A] for the same value of A
//  //   - Optional arguments must be of type Option, NOT Some or None
//  usersTable.filter(_.id === Option(32))
//
//
//  // Controlling queries with Sort, Take and Drop
//  // - To sort multiple columns, return a tuple of columns
//  // - Use take to show only the first n rows
//  //
//  //
//  // Scala Code	  SQL Equivalent
//  // ===========================
//  // sortBy	      ORDER BY
//  // take	        LIMIT
//  // drop	        OFFSET
//  usersTable.sortBy(_.lName) // Ordered by lName
//  usersTable.sortBy(_.lName.desc) // Ordered by lName in reverse order
//  usersTable.sortBy(u => (u.fName, u.lName)) // Sorts by fName and lName
//
//  usersTable.sortBy(_.lName).take(5) // Sort by lName and take the first 5 rows
//  usersTable.sortBy(_.lName).drop(5).take(5) // Allows the grabbing of the next 5 rows by dropping the first 5
//  // Equivalent to
//  //  select "sender", "content", "id"
//  //  from "message"
//  //  order by "sender"
//  //  limit 5 offset 5
//
//  // Sorting null columns
//  // - Slick has three modifiers for use with 'desc' and 'asc': nullsFirst, nullsDefault, nullsLast
//  usersTable.sortBy(_.lName.nullsLast)
//
//
//  // Conditional Filtering
//  // - filterOpt and filterIf help with dynamic queries, where rows may or may not be filtered dependant on a condition.
//  //
//  // Pretend lName is optional
//
//  // Valid query, but will return no results if passed None
//  def optionalFilterQuery1(lName: Option[String]) =
//    usersTable.filter(user => user.lName === lName)
//
//  // filterOpt filters only when we have a value
//  // - returns everything if the filter doesn't run
//  def optionalFilterQuery2(lName: Option[String]) =
//    usersTable.filterOpt(lName)((row, value) => row.lName === value)
//
//  def queryShortHand(lName: Option[String]) =
//    usersTable.filterOpt(lName)(_.lName === _)
//
//  // filterIf turns a 'where' condition on or off
//  val hideUsers = false
//
//  // The query in the curry runs if hideUsers is true
//  val queryIf = usersTable.filterIf(hideUsers)(_.id < 0)
//
//  // filterIf and filterOpt can be chained
//  val chainedQuery = queryShortHand(Option("Fred")).filterIf(hideUsers)(_.id < 0)
//
//
//
//
//
//  // Creating and Modifying data
//  // - insert, update and delete queries
//  //
//  // Inserting rows into tables
//  // - +=   1 row
//  // - ++=  multiple rows
//  //
//  // += creates a DBIOAction immediately without an intermediate query
//  val insertAction: MySQLProfile.ProfileAction[Int, NoStream, Effect.Write] = usersTable += User(9, "Fred", "Jenkins", 23)
//  //
//  //
//  // Primary Key Allocation
//  // - Specify an option on the column definition for primary key and auto incrementing
//  //
//  // def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
//  //
//  // - Actions can be forced with the forceInsert method, such as overriding O.AutoInc
//  usersTable forceInsert User(32, "Fred", "Jenkins", 23)
//  //
//  //
//  // Retrieve a primary key on insert with the returning method
//  val insertOfFredPk: DBIO[Int] = usersTable returning usersTable.map(_.id) += User(13, "Fred", "Bob", 90)
//  //
//  // This query always returns a PK
//  lazy val usersTableReturningId = usersTable returning usersTable.map(_.id)
//  val theId = db.run(usersTable += User(14, "Fred", "Jenkins", 23))
//  //
//  //
//  // Retrieving a whole inserted record
//  // - Only some dbs support (H2 only supports PK)
//  // - https://scala-slick.org/doc/3.3.3/supported-databases.html
//  //   - jdbc.returnInsertOther
//  usersTable returning usersTable += User(14, "Fred", "Jenkins", 23)
//  //
//  // We can fake a retrieval by adding the returned PK and adding it to the record that was inserted
//  val usersTableReturningRow = usersTableReturningId into { (user, id) => user.copy(id)}
//  val insertUserAction = usersTableReturningRow += User(67, "Fred", "Jenkins", 23)
//  //
//  //
//  // Inserting Specific Columns
//  // - map over the query before calling insert
//  usersTable.filter(_.id === 1).map(_.lName) += "Grog"
//  //
//  //
//  // Inserting multiple rows
//  //
//  // Prepare one SQL statement for reuse with each row
//  val batchInsert = Seq(
//    User(68, "Fred", "Jenkins", 23),
//    User(69, "Fred", "Jenkins", 23),
//    User(70, "Fred", "Jenkins", 23),
//    User(71, "Fred", "Jenkins", 23),
//    User(72, "Fred", "Jenkins", 23)
//  )
//  // Batch version of usersTable returning is available
//  usersTableReturningRow ++= batchInsert
//  //
//  //
//  // More control over inserts
//  // - Insert data based on another query using forceInsertQuery
//  // - selectExpression must match the columns required by insertExpression
//  //     insertExpression.forceInsertQuery(selectExpression)
//  val dataToInsert = Query("Fred", "Grubb") // returns a fixed value, tuple of two columns
//  val dataAlreadyExists = usersTable.filter(u => u.fName === "Fred" && u.lName === "Grubb").exists
//  //
//  // to use the data when the row doesn't exist, call filterNot
//  val selectExpression = dataToInsert.filterNot(_ => dataAlreadyExists)
//  val forceAction = usersTable.map(u => u.fName -> u.lName)
//                              .forceInsertQuery(selectExpression)
//  // forceAction is equivalent to
//  //  insert into "usersTable" ("fName", "lName")
//  //    select 'Fred', 'Grubb'
//  //  where
//  //    not exists(
//  //      select "id", "fName", "lName", "age"
//  //      from "messages"
//  //      where "fName" = 'Fred' and "lName" = 'Grubb')
//  //
//  //
//  // Deleting Rows
//  // - use the delete method
//  // - do not use with map (incompatible)
//  // - delete using filter, then call delete
//  // - delete can only be used with a TableQuery
//  usersTable.filter(_.id === 3).delete
//  //
//  // Updating Rows
//  // - Create a query to select rows to modify and columns to change
//  val updateQuery = usersTable.filter(_.id === 5).map(u => u.fName -> u.lName)
//  // - Call the update method
//  updateQuery.update("Bobby", "Simmons")
//  //
//  // Case classes can be used as a parameter to update
//  case class NameUpdate(fName: String, lName: String)
//  val newValue = NameUpdate("Granny", "Lola")
//  val newUpdateQuery = usersTable.filter(_.id === 5)
//                                 .map(u => (u.fName -> u.lName).mapTo[NameUpdate])
//                                 .update(newValue)
//  //
//  // Updating with computed values
//  //
//  // - Plain SQL is recommended
//  //  update "message" set "content" = CONCAT("content", '!')
//
//
//
//
//  // Combining Actions
//  // - Action combinators turn numerous actions into a single action
//  //   - Can be run by itself once combined, or as part of a transaction
//  // - DONT run an action, use the result, then run another action as dealing with multiple futures is hell
//  // - Focus on Actions and how they combine together
//  //
//  // COMBINED ACTIONS ARE NOT AUTOMATICALLY TRANSACTIONS!
//  //
//  //  Combinators for DBIOAction/DBIO[T]
//  //  - EC indicates Execution Context required
//  //
//  //  Method	        Arguments	                Result Type
//  //  ============================================================
//  //  map (EC)	      T => R	                  DBIO[R]
//  //  flatMap (EC)	  T => DBIO[R]	            DBIO[R]
//  //  filter (EC)	    T => Boolean	            DBIO[T]
//  //  named	          String	                  DBIO[T]
//  //  zip	            DBIO[R]	                  DBIO[(T,R)]
//  //  asTry		                                  DBIO[Try[T]]
//  //  andThen or >>	  DBIO[R]	                  DBIO[R]
//  //  andFinally	    DBIO[_]	                  DBIO[T]
//  //  cleanUp (EC)	  Option[Throwable]=>DBIO[_]	DBIO[T]
//  //  failed		                                DBIO[Throwable]
//  //
//  //  Type simplified for DBIO
//  //  sequence	      TraversableOnce[DBIO[T]]	DBIO[TraversableOnce[T]]
//  //  seq	            DBIO[_]*	                DBIO[Unit]
//  //  from	          Future[T]	                DBIO[T]
//  //  successful	    V	                        DBIO[V]
//  //  failed	        Throwable	                DBIO[Nothing]
//  //  fold (EC)	      (Seq[DBIO[T]], T) (T,T)=>T	DBIO[T]
//  //
//  // andThen
//  // - Runs one action after the other, discarding the result of the first action and returning the result of the second
//  usersTable.filter(_.lName === "Grobb").result andThen usersTable.size.result
//  //
//  // DBIO.seq
//  // - Combines multiple actions
//  // - Discards all return values
//  DBIO.seq(initUsersTable, dropUserTable)
//  //
//  // Map
//  // - Use to set up the transformation query of a value from a db
//  //   - transformation runs when result of action returns from db
//  //
//  // DBIO.successful and DBIO.failed
//  // - Create an action to represent a simple value using DBIO.successful
//  // - The value for failures if Throwable
//  val ok: DBIO[Int] = DBIO.successful(100)
//  val err: DBIO[Nothing] = DBIO.failed(new RuntimeException("Uh oh"))
//  //
//  // flatMap
//  // - Allows the sequencing of actions and deciding what to do at each step
//  val delete: DBIO[Int] = usersTable.delete
//  def insert(count: Int) = usersTable += User(90, "Fred", "Jenkins", 23)
//
//  val resetUsers = delete.flatMap { count => insert(count)}
//  //
//  // Can also use flatMap to control which actions are run
//  val logResetAction: DBIO[Int] =
//    delete.flatMap {
//      case 0 => DBIO.successful(0) // 0 if no message inserted
//      case n => insert(n)
//    }
//  //
//  //
//  // DBIO.sequence
//  // - Takes a sequence of DBIOs and returns a sequence of DBIOs
//  def reverseUserFirstName(user: User): DBIO[Int] =
//    usersTable.filter(_.id === user.id).
//    map(_.fName).
//    update(user.fName.reverse)
//
//  // Wrap a Seq[DBIO] with DBIO.sequence to give a single DBIO[Seq[Int]]
//  // - use flatMap to combine the sequence with the original query
//  val updateFirstNameActions = {
//    usersTable.result
//      .flatMap(users => DBIO.sequence(users.map(reverseUserFirstName)))
//  }
//  //
//  //
//  // DBIO.fold
//  // - Can be used to combine values
//  val r1: DBIO[Int] = DBIO.successful(233)
//  val r2: DBIO[Int] = DBIO.successful(54)
//
//  val reportsList: List[DBIO[Int]] = r1 :: r2 :: Nil
//
//  // Reports can be folded with a function
//  // Consider starting pos
//  val default: Int = 0
//  // produce action to summarise reports
//  // - results are combined by the function provided to fold
//  val summary: DBIO[Int] = DBIO.fold(reportsList,default) {
//    (r1, r2) => r1 + r2 // 233 + 54
//  }
  //
  //
  // DBIO.zip
  // - DBIO.seq combines actions ignoring the results
  // - andThen combines actions discarding the result of the first action
  // - Use zip to keep both results
  //
  // Gets total num of users, and users with id equal to 1
//  val zipped: DBIO[(Int, Seq[User])] = usersTable.size.result zip usersTable.filter(_.id === 1).result
  //
  //
  // andFinally and cleanUp
  // - Act a little like catch and finally in Scala
  // - cleanUp runs after an action completes in response to a failed action and
  //   has access to any error info as a Option[Throwable]
  // - andFinally runs regardless of success or failure, no access to error info
//  def logAction(err: Throwable): DBIO[Int] = {
//    println(err.toString)
//    DBIO.successful(0)
//  }
//
//  // important work that may fail
//  val someWork = DBIO.failed(new RuntimeException("Ooops"))
//
//  val actionable: DBIO[Int] = someWork.cleanUp {
//    case Some(value) => logAction(value)
//    case None => DBIO.successful(0)
//  }
  //
  //
  // asTry
  // - Converts an actions type from DBIO[T] to DBIO[Try[T]], allows working with Success[T] and Failure instead of
  //   exceptions
//  val tryAction = DBIO.failed(new RuntimeException("umm"))
//  val returned: Future[Try[Nothing]] = db.run(tryAction.asTry) // successful actions evaluate to Success[T]
  //
  //
  // Logging Queries and Results
  // - Slick uses logging interface SLF4J
  // - src/main/resources/logback.xml
  //
  // <logger name="slick.jdbc.JdbcBackend.statement" level="DEBUG"/>
  //
  // Slick loggers:
  //  Logger	                            Will log…
  //  slick.jdbc.JdbcBackend.statement	  SQL sent to the database.
  //  slick.jdbc.JdbcBackend.parameter	  Parameters passed to a query.
  //  slick.jdbc.StatementInvoker.result	The first few results of each query.
  //  slick.session	                      Session events such as opening/closing connections.
  //  slick	                              Everything!
  //
  //
  // Transactions
  // - The transactionally method allows sets of modifications to be run together as a transaction
  //     - actions either all succeed, or all fail
  //
  // Changes made are temporary until the sequence of actions has finished executing successfully
  // - No changes are committed if an action fails
//  val willRollbackIfFails = (
//    (usersTable += User(99, "Fred", "Jenkins", 23)) andThen
//      (usersTable += User(101, "Fred", "Jenkins", 23)) andThen
//      DBIO.failed(new RuntimeException("uh oh"))
//  ).transactionally
//
//  db.run(willRollbackIfFails.asTry)


  // Data modelling
  // - how to structure apps
  // - alternatives to modelling rows as case classes
  // - store richer data types in columns
  // - optional values and foreign keys

  // Abstracting a db
  // Slick provides a common supertype for DB profiles called JdbcProfile
  // - import slick.jdbc.JdbcProfile cannot be imported directly. Instead, inject a dependency of
  //   type JdbcProfile into app and import from that
  //
  // Pattern to follow:
  //
  // 1. isolate our database code into a trait (or a few traits);
  //
  // 2. declare the Slick profile as an abstract val and import from that; and
  //
  // 3. extend our database trait to make the profile concrete.
  //
  // Possible trait:
//  trait DatabaseModule {
//    // Declare an abstract profile:
//    val profile: JdbcProfile
//
//    // Import the Slick API from the profile:
//    import profile.api._
//
//    // Write our database code here...
//  }
  //
  // - profile is declared with an abstract val
  // - compiler will bind our selected profile as it is immutable (polymorphism ok here)
  //
  // Example instantiation:
  // // Instantiate the database module, assigning a concrete profile:
  //  val databaseLayer = new DatabaseModule {
  //    val profile = slick.jdbc.H2Profile
  //  }
  //
  // Scaling to larger codebases:
  // - The above pattern can be extended to a family of traits
//  trait Profile {
//    val profile: JdbcProfile
//  }
//
//  trait DatabaseModule1 { self: Profile =>
//    import profile.api._
//
//    // Write database code here
//  }
//
//  trait DatabaseModule2 { self: Profile =>
//    import profile.api._
//
//    // Write more database code here
//  }
//
//  // Mix the modules together:
//  class DatabaseLayer(val profile: JdbcProfile) extends
//    Profile with
//    DatabaseModule1 with
//    DatabaseModule2
//
//  // Instantiate the modules and inject a profile:
//  object Main2 extends App {
//    val databaseLayer = new DatabaseLayer(slick.jdbc.H2Profile)
//  }
}

// Query is used to build SQL for a single query. Calls to map and filter modify clauses to the SQL,
// but only one query is created.
//
// Query[M, U, C]
//  - M = mixed type, the function parameter type seen when calling methods like map and filter,
//  - U = unpacked type, type collected in our results
//  - C = collection type, type of collection results are accumulated into
//
// Constant Queries not related to any table can be constructed
// val exampleConstantQuery = Query(1)
//  exampleConstantQuery.result.statements.mkString = "select 1"

// DBIOAction is used to build sequences of SQL queries. Calls to map and filter chain queries
// together and transform their results once they are retrieved in the database. DBIOAction is also
// used to delineate transactions.
//
// Future is used to transform the asynchronous result of running a DBIOAction. Transformations on
// Futures happen after we have finished speaking to the database.
