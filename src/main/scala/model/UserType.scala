package model

// Only extendible in this file
sealed trait UserType

// Enumerations
case object Customer extends UserType
case object StoreKeeper extends UserType
case object Cleaner extends UserType
case object StoreAssistant extends UserType
