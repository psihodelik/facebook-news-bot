## Guardian Facebook news bot

This is the app behind our Facebook Messenger "bot", which is attached to the Guardian's Facebook page.

#### What does it do?
We're still in the process of working out what a messenger bot can/should do. At the moment, it can:

1. Push a "morning briefing" to users who have subscribed (taking into account their timezone).
2. Respond with a carousel of links for a requested topic. These are either the top headlines, or most popular stories.

Users can choose from one of four regional editions - UK, US, Australia and International.

#### Architecture
The app is written in scala, and uses the [akka-http](http://doc.akka.io/docs/akka/2.4.11/scala/http/introduction.html) library. We deploy to AWS EC2s.

A minimal amount of user data is stored in Dynamodb. *Nothing* we store can be used to personally identify a user - it's just general data, like their morning briefing time and edition.

To allow this app to scale horizontally, the scheduling of the morning briefings is handled separately by an [AWS Lambda](https://github.com/guardian/facebook-news-bot-scheduler). The Lambda checks for users who are due to receive their morning briefings and adds them to an SQS queue, which this app polls.

#### Testing
Run `sbt test`.

The tests use [sbt-dynamodb](https://github.com/localytics/sbt-dynamodb) to run dynamodb locally.
A dummy CAPI service reads content data from file. A mock Facebook service is used to verify that the responses are correct.
  
#### Running locally
This shouldn't generally be necessary. You'll need to [set up dynamodb locally](http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html) and provide a local config file at `~/.configuration-magic/facebook-news-bot.properties`.