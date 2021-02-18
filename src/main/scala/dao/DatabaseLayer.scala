package dao

import slick.jdbc.JdbcProfile

// As the DB Modules have Profile as a self-type, profile can be shared across the family of modules.
// - traits specifying a self-type may only be extended by a class that also extends Profile
class DatabaseLayer(val profile: JdbcProfile) extends Profile with Tables {

}
