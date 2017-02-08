package com.gu.facebook_news_bot.models

case class UserNoms(ID: String,
                    bestPicture: Option[String] = None,
                    bestDirector: Option[String] = None,
                    bestActor: Option[String] = None,
                    bestActress: Option[String] = None,
                    oscarsNomsUpdateType: Option[String] = None)

object OscarTile {
  val imageUrl = "https://media.guim.co.uk/76c5179cd03699f56f66f46dad557063cb26f567/0_0_1200_565/1200.png"
}

case class IndividualNominee(name: String, pictureUrl: String = OscarTile.imageUrl)

object Nominees {

  val bestPictureNominees = List(
    IndividualNominee("Arrival", "https://media.guim.co.uk/b6f55f8449cadad21e5cfc120c6ba6813595f004/0_0_3000_2000/3000.jpg"),
    IndividualNominee("Fences", "https://media.guim.co.uk/d9987ebab04545a08b173e20bd1888ddae476e26/0_0_7360_4912/7360.jpg"),
    IndividualNominee("Hacksaw Ridge", "https://media.guim.co.uk/ba72b1e31eb13af0ec5215dff687ab543c175058/0_0_670_377/670.jpg"),
    IndividualNominee("Hell or High Water", "https://media.guim.co.uk/d19947d0a69b6430ed04172aa4914f2bcbf2950a/0_0_670_377/670.jpg"),
    IndividualNominee("Hidden Figures", "https://media.guim.co.uk/115d79cfed737f7d896d9764be28ab52282f2ea7/0_0_2400_1603/2400.jpg"),
    IndividualNominee("La La Land", "https://media.guim.co.uk/3b02f4d46e38f0b1b9d397617fd1f6024cdb2d65/0_0_3600_2479/3600.jpg"),
    IndividualNominee("Lion", "https://media.guim.co.uk/de2769917616cfe125120f33509b6aa8e6a55ded/0_0_1000_563/1000.jpg"),
    IndividualNominee("Manchester by the Sea", "https://media.guim.co.uk/0b5109c5f8549ef62f943a6fd2f122e981ec2ba7/0_0_1000_666/1000.jpg"),
    IndividualNominee("Moonlight", "https://media.guim.co.uk/fa870cb4be98c9c0062b328d5fda40b31b33b0c3/0_0_2048_1365/2048.jpg")
  )

  val bestDirectorNominees = List(
    IndividualNominee("Damien Chazelle with La La Land", "https://media.guim.co.uk/546b964cfbbfb23ecc57b6c2aa437af099ff9cf3/0_0_4000_2185/4000.jpg"),
    IndividualNominee("Mel Gibson with Hacksaw Ridge", "https://media.guim.co.uk/d5ca2c20b1a2b8ad5da353e4575eaecffac3ce59/0_20_2709_1458/2709.jpg"),
    IndividualNominee("Barry Jenkins with Moonlight", "https://media.guim.co.uk/4b2ff3f5958d76decb77351fffcef43ccbe7d693/0_74_4096_2732/4096.jpg"),
    IndividualNominee("Kenneth Lonergan with Manchester by the Sea", "https://media.guim.co.uk/576442180e93b8aee7840b41414b5fa6efe44650/0_78_3500_1850/3500.jpg"),
    IndividualNominee("Denis Villeneuve with Arrival", "https://media.guim.co.uk/2debb1599af21e77c0a375acc4f81ee3942d3030/0_0_3064_1610/3064.jpg")
  )

  val bestActressNominees = List(
    IndividualNominee("Isabelle Huppert in Elle", "https://media.guim.co.uk/ee7f6a1460d06ca446541c5e14719df0e466d394/333_0_3171_1572/3171.jpg"),
    IndividualNominee("Ruth Negga in Loving", "https://media.guim.co.uk/e85b6de3a284ce7019a0b725cee42ecec95a81e6/167_0_1185_599/1185.jpg"),
    IndividualNominee("Natalie Portman in Jackie", "https://media.guim.co.uk/b37c974b93bd4dd599e223004b5ebe6feced51a6/244_241_2358_1183/2358.jpg"),
    IndividualNominee("Emma Stone in La La Land", "https://media.guim.co.uk/9fbe5373b645cd98ac5dcf4e19d0fbceb8925b80/0_281_1323_689/1323.jpg"),
    IndividualNominee("Meryl Streep in Florence Foster Jenkins", "https://media.guim.co.uk/3c2abf8ee3eda6b592a9c55c67f14f51f0a96d0b/198_0_2984_1526/2984.jpg")
  )

  val bestActorNominees = List(
    IndividualNominee("Casey Affleck in Manchester by the Sea", "https://media.guim.co.uk/e00ec6c105ff4e178e58a7633bcd21c8d704bb1e/145_17_1529_836/1529.jpg"),
    IndividualNominee("Andrew Garfield in Hacksaw Ridge", "https://media.guim.co.uk/b8367f597aeb23bef30c97508b6c4ba7d91092d4/345_12_2899_1422/2899.jpg"),
    IndividualNominee("Ryan Gosling in La La Land", "https://media.guim.co.uk/52299781e5a7b67931406c63a2587df629ceecd3/0_139_2164_1150/2164.jpg"),
    IndividualNominee("Viggo Mortensen in Captain Fantastic", "https://media.guim.co.uk/20aacae7400c7cdd5e02c449c9366d4f4e5483c8/188_0_3316_1682/3316.jpg"),
    IndividualNominee("Denzel Washington in Fences", "https://media.guim.co.uk/8dbac767193615bd9aa851f2f0048f8daae7e799/455_0_6905_3699/6905.jpg")
  )

}
