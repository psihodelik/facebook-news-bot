package com.gu.facebook_news_bot.models

case class UserNoms(ID: String,
                    bestPicture: Option[String] = None,
                    bestDirector: Option[String] = None,
                    bestActor: Option[String] = None,
                    bestActress: Option[String] = None)

case class IndividualNominee(name: String, pictureUrl: String)

object Nominees {

  val defaultPictureUrl = "https://media.guim.co.uk/82ef416b00febc774665554973c2965c7f82b819/0_191_2266_1360/140.jpgq"

  val bestPictureNominees = List(
    IndividualNominee("Arrival",defaultPictureUrl),
    IndividualNominee("Fences", defaultPictureUrl),
    IndividualNominee("Hacksaw Ridge", defaultPictureUrl),
    IndividualNominee("Hell or High Water", defaultPictureUrl),
    IndividualNominee("Hidden Figures", defaultPictureUrl),
    IndividualNominee("La La Land", defaultPictureUrl),
    IndividualNominee("Lion", defaultPictureUrl),
    IndividualNominee("Manchester by the Sea", defaultPictureUrl),
    IndividualNominee("Moonlight", defaultPictureUrl)
  )

  val bestDirectorNominees = List(
    IndividualNominee("Damien Chazelle for La La Land", defaultPictureUrl),
    IndividualNominee("Mel Gibson for Hacksaw Ridge", defaultPictureUrl),
    IndividualNominee("Barry Jenkins for Moonlight", defaultPictureUrl),
    IndividualNominee("Kenneth Lonergan for Manchester by the Sea", defaultPictureUrl),
    IndividualNominee("Denis Villeneuve for Arrival", defaultPictureUrl)
  )

  val bestActressNominees = List(
    IndividualNominee("Isabelle Huppert in Elle", defaultPictureUrl),
    IndividualNominee("Ruth Negga in Loving", defaultPictureUrl),
    IndividualNominee("Natalie Portman in Jackie", defaultPictureUrl),
    IndividualNominee("Emma Stone in La La Land", defaultPictureUrl),
    IndividualNominee("Meryl Streep in Florence Foster Jenkins", defaultPictureUrl)
  )

  val bestActorNominees = List(
    IndividualNominee("Casey Affleck in Manchester by the Sea", defaultPictureUrl),
    IndividualNominee("Andrew Garfield in Hacksaw Ridge", defaultPictureUrl),
    IndividualNominee("Ryan Gosling in La La Land", defaultPictureUrl),
    IndividualNominee("Viggo Mortensen in Captain Fantastic", defaultPictureUrl),
    IndividualNominee("Denzel Washington in Fences", defaultPictureUrl)
  )

}


