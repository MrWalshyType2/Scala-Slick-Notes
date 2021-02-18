package dao

import slick.dbio.Effect
import slick.dbio.Effect.{Read, Transactional, Write}

sealed trait ReadWrite extends Effect with Read with Write with Transactional {

}
