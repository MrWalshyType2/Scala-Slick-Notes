## Projections, `ProvenShapes`, `mapTo`, and `<>`

Tables in Slick require a **default projection**, implemented with the function `*`.

```scala 3
// Table[(String, Int)] tuple
class MyTable(tag: Tag) extends Table[(String, Int)](tag, "myTable") {

  def column1 = column[String]("column1")
  def column2 = column[Int]("column2")

  // maps to the defined tuple
  def * = (column1, column2)
}
```

> - Information can be hidden by excluding it from a row definition.
> - Projections provide mappings between DB columns and Scala values.

Definition for `*` in the `Table` class:
```scala 3
abstract class Table[T] {
  def * : ProvenShape[T]
}
```

Slick uses implicit conversions to build a `ProvenShape` object from any provided columns. Slick is able to use any Scala type provided it can generate a compatible `ProvenShape`.

1. A single `column` definition will produce a shape capable of mapping the columns content to a value of the columns type parameter. A column of `Rep[Int]` maps a value of type Int.
2. Tuples of DB columns map tuples to their specified type parameters. `(Rep[String], Rep[Int])` is mapped to `(String, Int)`:

```scala 3
class MyTable2(tag: Tag) extends Table[(String, Int)](tag, "mytable") {

  def column1 = column[String]("column1")
  def column2 = column[Int]("column2")

  def * = (column1, column2)
}
```

3. A `ProvenShape[A]` can be converted to a `ProvenShape[B]` using the *projection operator*, `<>`.

```scala 3
case class User(name: String, id: Long)

class UserTable3(tag: Tag) extends Table[User](tag, "user") {

  def id   = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")

  def * = (name, id).<>(User.tupled, User.unapply)
}
```

Two arguments to `<>`:

> - function `A => B`
> - function `B => Option[A]`

## Table & Column Representation

### Nullable Columns

SQL columns are nullable by default (`NULL`). Columns in Slick are non-nullable; to model a nullable column in Scala, use `Option[T]`.

#### Selecting rows with nulled fields

Expressions in SQL evaluate `null` to `null`, `'Eli' === 'Lucy'` evaluates to `false` whereas `'Eli' === 'null'` evaluates to `null`. SQL's `null` values is falsey, this means it never returns a value.

Do not do the following:

```scala 3
val none: Option[String] = None
val badQuery = users.filter(_.email === none).result

// badQuery is roughly SELECT * FROM "user" WHERE "email" = NULL
```

SQL provides two operators to solve this problem, Slick provides two methods as equivalents which can act on any `Rep[Option[A]]`:

```
Scala Code      Operand Column Types    Result Type	    SQL Equivalent

===============================================================

col.?	        A	                Option[A]	    col

col.isEmpty	Option[A]	        Boolean	            col is null

col.isDefined	Option[A]	        Boolean	            col is not null
```

The query can be fixed with:

```scala 3
val users = users.filter(_.email.isEmpty).result
// SELECT * FROM "user" WHERE "email" IS NULL
```

### Primary Keys

ID fields set like so have been seen:

```scala 3
def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
```

The options `O.PrimaryKey` and `O.AutoInc` do two things:

> - modify the SQL generated for DDL statements
> - _.AutoInc removes the corresponding column from the SQL generated for `INSERT` statements, this allows the DB to auto-increment the value.

Instead of modelling the ID in a case class with a default value, such as `id: Long = 0L`, some devlopers prefer to wrap primary key values in `Options`:

A model that uses `None` as the primary key for an unsaved record, `Some` for a saved record has advantages and disadvantages:

> - Easier to identify unsaved records
> - Harder to get the value of a primary key for use in a query

```scala 3
case class User(id: Option[Long], name: String)

class UserTable(tag: Tag) extends Table[User](tag, "user") {
  def id     = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name   = column[String]("name")

  def * = (id.?, name).mapTo[User]
}

lazy val users = TableQuery[UserTable]
lazy val insertUser = users returning users.map(_.id)
```

The primary key in the above table is not optional, `None` represents an unsaved value - the DB will insert a primary key for us. `None` will never be retrieved from the database.

Mapping non-nullable DB columns to an optional field value is handled by the `?` operator in the default projection.

> Converts a `Rep[A]` to a `Rep[Option[A]]`

### Compound Primary Keys

Primary keys can also be declared in another way:

```scala 3
def id = column[Long]("id", O.AutoInc)
def pk = primaryKey("pk_id", id)
// ALTER TABLE "user" ADD CONSTRAINT "pk_id" PRIMARY KEY("id")
```

The `primaryKey` method allows compound primary keys to be defined that involve multiple columns.

```scala 3
case class Garage(name: String, location: String, id: Long = 0L)

class GarageTable(tag: Tag) extends Table[Room](tag, "garage") {

 def id    = column[Long]("id", O.PrimaryKey, O.AutoInc)
 def name = column[String]("name")
 def location = column[String]("location")
 def * = (name, location, id).mapTo[Garage]
}

lazy val garages = TableQuery[GarageTable]
lazy val insertGarage = garages returning garages.map(_.id)
```

Table for relating Vehicle's to Garage's:

```scala 3
case class Ticket(vehicleId: Long, garageId: Long)

class TicketTable(tag: Tag) extends Table[Ticket](tag, "ticket") {

 def vehicleId = column[String]("name")
 def garageId = column[String]("location")

 def pk = primaryKey("garage_vehicle_pk", (vehicleId, garageId))

 def * = (vehicleId, garageId).mapTo[Ticket]
}

lazy val tickets = TableQuery[TicketTable]
```

> Composite primary keys can be defined with tuples or `HList`s of columns (Slick will generate a `ProvenShape` and inspect it to find the list of columns involved)

The `tickets` table can be used like any other:

```scala 3
val program: DBIO[Int] = for {
 _ <- garages.schema.create
 _ <- tickets.schema.create
 mazdaId <- insertVehicle += Vehicle("Mazda", "Fred Jenkins")
 garageId <- insertGarage += Garage("Longside Repairs", "Longside Avenue")
 // Put Vehicle in Garage tickets table
 rowsAdded <- tickets += Ticket(mazdaId, garageId)
} yield rowsAdded
```

### Indices

> Indices increase the efficiency of database queries at the cost of higher disk usage.

```scala 3
class IndexExample(tag: Tag) extends Table[(String,Int)](tag, "people") {
  def name = column[String]("name")
  def age  = column[Int]("age")

  def * = (name, age)

  def nameIndex = index("name_idx", name, unique=true)
  def compoundIndex = index("c_idx", (name, age), unique=true)
}
```

### Foreign Keys

> Foreign keys are declared in a similar manner to compound primary keys.

The method `foreignKey` takes four parameters:

> - name
> - column/columns that make up the foreign key
> - `TableQuery` the foreign key belongs to
> - function on the supplied `TableQuery[T]` taking the supplied column(s) as params and returning an instance of `T`

#### Using a foreign key to connect a `message` to a `user`

```scala 3
case class Message(
  senderId : Long,
  content  : String,
  id       : Long = 0L)

class MessageTable(tag: Tag) extends Table[Message](tag, "message") {
  def id       = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def senderId = column[Long]("sender")
  def content  = column[String]("content")

  def * = (senderId, content, id).mapTo[Message]

  def sender = foreignKey("sender_fk", senderId, users)(_.id)
}

lazy val messages = TableQuery[MessageTable]
```

The `foreignKey` gives two things, the first is a constraint added to the generated DDL statement:

```
ALTER TABLE "message" ADD CONSTRAINT "sender_fk"
  FOREIGN KEY("sender") REFERENCES "user"("id")
  ON UPDATE NO ACTION
  ON DELETE NO ACTION
```

> On Update and On Delete
>> Foreign keys make guarantees about the data stored, for the above there must be a `sender` in the `user` table to successfully insert a new message.
>>
>> If a change occurs on a `user` row, any number of referential actions may be triggered. The default is for nothing to happen.
>>
>> ```scala 3
>> class AltMsgTable(tag: Tag) extends Table[Message](tag, "message") {
>>   def id       = column[Long]("id", O.PrimaryKey, O.AutoInc)
>>   def senderId = column[Long]("sender")
>>   def content  = column[String]("content")
>> 
>>   def * = (senderId, content, id).mapTo[Message]
>> 
>>   def sender = foreignKey("sender_fk", senderId, users)(_.id, onDelete=ForeignKeyAction.Cascade)
>> }
>> ```
>> 
>> The above will delete all messages associated with a user should a user be deleted.
>>
>> Slick supports five actions for `onUpdate` and `onDelete`:
>> ```
>> Action	        Description
>> NoAction	The default.
>> Cascade	        A change in the referenced table triggers a change in the referencing table. In our example, deleting a user will cause their messages to be deleted.
>> Restrict	Changes are restricted, triggered a constraint violation exception. In our example, you would not be allowed to delete a user who had posted a message.
>> SetNull	        The column referencing the updated value will be set to NULL.
>> SetDefault      The default value for the referencing column will be used. Default values are discussion in Table and Column Modifiers, later in this chapter.
>> ```

The second is a query that can be used in a join:

```scala 3
val q = for {
  msg <- messages
  usr <- msg.sender
} yield (usr.name, msg.content)

// SELECT u."name", m."content"
// FROM "message" m, "user" u
// WHERE "id" = m."sender"
```

Usage:

```scala 3
def findUserId(name: String): DBIO[Option[Long]] =
  users.filter(_.name === name).map(_.id).result.headOption

def findOrCreate(name: String): DBIO[Long] =
  findUserId(name).flatMap { userId =>
    userId match {
      case Some(id) => DBIO.successful(id)
      case None     => insertUser += User(None, name)
    }
}

// Populate the messages table:
val setup = for {
  daveId <- findOrCreate("Dave")
  halId  <- findOrCreate("HAL")

  // Add some messages:
  _         <- messages.schema.create
  rowsAdded <- messages ++= Seq(
    Message(daveId, "Hello, HAL. Do you read me, HAL?"),
    Message(halId,  "Affirmative, Dave. I read you."),
    Message(daveId, "Open the pod bay doors, HAL."),
    Message(halId,  "I'm sorry, Dave. I'm afraid I can't do that.")
  )
} yield rowsAdded
```

#### FOREIGN KEY RULE

Use `lazy val` for `TableQuery`s and `def` for foreign keys (for consistency with `column` definitions)

### Column Options

> - `O.Length`
> - `O.SqlType`
> - `O.Unique`
> - `O.Default`
> - `O.PrimaryKey`
> - `O.AutoInc`

## Custom Column Mappings

Slick can map tuples and `HList`s of columns to case classes.

> Slick can also control how individual columns are mapped to Scala types.
>> For example, using the Joda Time's `DateTime` class is not immediately possible, Slick does not have native support for Joda Time. It can be painlessly implemented via Slick's `ColumnType` type class:
>> ```scala 3
>> import java.sql.Timestamp
>> import org.joda.time.DateTime
>> import org.joda.time.DateTimeZone.UTC
>> 
>> object CustomColumnTypes {
>> 
>>   implicit val jodaDateTimeType =
>>     MappedColumnType.base[DateTime, Timestamp](
>>       dt => new Timestamp(dt.getMillis),
>>       ts => new DateTime(ts.getTime, UTC)
>>     )
>> }
>> ```
>>
>> Providing two functions to `MappedColumnType.base`:
>> 
>> - one from a `DateTime` to a DB friendly `java.sql.Timestamp`
>> - one from `java.sql.Timestamp` to `DateTime`
>>
>> Now the `DateTime` type can be used in a case class:
>>
>> ```scala 3
>> case class Message(
>>   senderId  : Long,
>>   content   : String,
>>   timestamp : DateTime,
>>   id        : Long = 0L)
>> 
>> class MessageTable(tag: Tag) extends Table[Message](tag, "message") {
>> 
>>   // Bring our implicit conversions into scope:
>>   import CustomColumnTypes._
>> 
>>   def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
>>   def senderId  = column[Long]("sender")
>>   def content   = column[String]("content")
>>   def timestamp = column[DateTime]("timestamp")
>> 
>>   def * = (senderId, content, timestamp, id).mapTo[Message]
>> }
>> 
>> lazy val messages = TableQuery[MessageTable]
>> lazy val insertMessage = messages returning messages.map(_.id)
>> ```

### Value Classes
Using a `Long` for a primary key is good at the DB level, not at the application level. Silly mistakes can be made as a `Long` could be a primary key for different models and easily be confused.

Prevent this with types, model primary keys using value classes:

```scala 3
case class MessagePK(value: Long) extends AnyVal
case class UserPK(value: Long) extends AnyVal
```

A *value class* is a compile-time wrapper around a value. At runtime, the wrapper will go away leaving no allocation or performance overhead in the running code.

> To use a value class, Slick needs to be provided with `ColumnType`s to use value types in our tables.

```scala 3
implicit val messagePKColumnType =
  MappedColumnType.base[MessagePK, Long](_.value, MessagePK(_))

implicit val userPKColumnType =
   MappedColumnType.base[UserPK, Long](_.value, UserPK(_))
```

Slick provides a shorthand version called `MappedTo` which handles this for us:

```scala 3
case class MessagePK(value: Long) extends AnyVal with MappedTo[Long]
case class UserPK(value: Long) extends AnyVal with MappedTo[Long]
```

> Using `MappedTo` means separate `ColumnType`. `MappedTo` will work with any class that:
>> 
>> - has a method called `value` returning the underlying DB value
>> - has a single-parameter constructor to create the Scala value from the DB

Redefining `User` and `Message` might look like:

```scala 3
case class User(name: String, id: UserPK = UserPK(0L))

class UserTable(tag: Tag) extends Table[User](tag, "user") {
  def id   = column[UserPK]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def * = (name, id).mapTo[User]
}

lazy val users = TableQuery[UserTable]
lazy val insertUser = users returning users.map(_.id)

///////////////////////////////////////////////////////////////////////////

case class Message(
  senderId : UserPK,
  content  : String,
  id       : MessagePK = MessagePK(0L))

class MessageTable(tag: Tag) extends Table[Message](tag, "message") {
  def id        = column[MessagePK]("id", O.PrimaryKey, O.AutoInc)
  def senderId  = column[UserPK]("sender")
  def content   = column[String]("content")

  def * = (senderId, content, id).mapTo[Message]

  def sender = foreignKey("sender_fk", senderId, users) (_.id, onDelete=ForeignKeyAction.Cascade)
}

lazy val messages      = TableQuery[MessageTable]
lazy val insertMessage = messages returning messages.map(_.id)
```

This stops values from being looked up with the wrong key:

```scala 3
users.filter(_.id === UserPK(0L)) // Legal
users.filter(_.id === MessagePK(0L)) // Illegal
```

Value classes are a low-cost method of making code safer (type-safety) and more legible. Larger databases can generate quite a bit of overhead, sometimes it can be easier to use code generation or generalise the primary key type:

```scala 3
case class PK[A](value: Long) extends AnyVal with MappedTo[Long]

case class User(
  name : String,
  id   : PK[UserTable])

class UserTable(tag: Tag) extends Table[User](tag, "user") {
  def id    = column[PK[UserTable]]("id", O.AutoInc, O.PrimaryKey)
  def name  = column[String]("name")

  def * = (name, id).mapTo[User]
}

lazy val users = TableQuery[UserTable]

val exampleQuery =
  users.filter(_.id === PK[UserTable](0L))
```

> Type-safety without the boiler plate of multiple PK type defs

### Modelling Sum Types

Case classes can model data.

> Using the language of *algebraic data types*, case classes are "product types" created from conjunctions of their field types (name AND age AND ...).
> The other common form of algebraic data type is a *sum type*, formed from a *disjunction* of other types (name OR age).

Flags for messages:

```scala 3
sealed trait Flag
case object Important extends Flag
case object Offensive extends Flag
case object Spam extends Flag

case class Message(
  senderId : UserPK,
  content  : String,
  flag     : Option[Flag] = None,
  id       : MessagePK = MessagePK(0L))
```

> Custom `ColumnType`s need to be created to manage the mapping:

```scala 3
implicit val flagType =
  MappedColumnType.base[Flag, Char](
    flag => flag match {
      case Important => '!'
      case Offensive => 'X'
      case Spam      => '$'
    },
    code => code match {
      case '!' => Important
      case 'X' => Offensive
      case '$' => Spam
    })
```

Sum types will cause the compiler to issue warnings if we add a new flag and don't add it to our `Flag => Char` function.

> Turn the warnings into errors by enabling the Scala compilers `-Xfatal-warnings` option

Flags can now easily be used like any other custom type:

```scala 3
class MessageTable(tag: Tag) extends Table[Message](tag, "flagmessage") {
  def id       = column[MessagePK]("id", O.PrimaryKey, O.AutoInc)
  def senderId = column[UserPK]("sender")
  def content  = column[String]("content")
  def flag     = column[Option[Flag]]("flag")

  def * = (senderId, content, flag, id).mapTo[Message]

  def sender = foreignKey("sender_fk", senderId, users)(_.id, onDelete=ForeignKeyAction.Cascade)
}

lazy val messages = TableQuery[MessageTable]
```

Querying for a message with a particular flag will require giving the compiler some help with the types:

```scala 3
messages.filter(_.flag === (Important : Flag)).result
```

The type annotation has two workarounds:

- Define a *smart constructor* method for each flag that returns it pre-cast as a `Flag`:

```scala 3
object Flags {
  val important : Flag = Important
  val offensive : Flag = Offensive
  val spam      : Flag = Spam

  val action = messages.filter(_.flag === Flags.important).result
}
```

- Define custom syntax to build filter expressions

```scala 3
implicit class MessageQueryOps(message: MessageTable) {
  def isImportant = message.flag === (Important : Flag)
  def isOffensive = message.flag === (Offensive : Flag)
  def isSpam      = message.flag === (Spam      : Flag)
}

messages.filter(_.isImportant).result.statements.head
```