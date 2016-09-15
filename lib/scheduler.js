"use strict"

module.exports = Scheduler

const Moment = require("moment")
const Events = require("./events").Events
const UserStore = require("./user-store")
const Capi = require("./capi")
const Facebook = require("./facebook")
const Schedule = require("node-schedule")
const Promise = require("promise")
const getUTCTime = require("./helpers").getUTCTime

/*
 *  Every 15 mins (the smallest interval between timezones), query the dynamo table
 *  for users with a notificationTimeUTC equal to current UTC time, and push to them.
 *  Also check if their timezone has changed since the user was last handled, and
 *  update if so.
 */
function Scheduler() {
  const recRule = new Schedule.RecurrenceRule()
  recRule.minute = [0, 15, 30, 45]
  Schedule.scheduleJob(recRule, this.run.bind(this))
}

Scheduler.prototype.run = function() {
  const now = Moment.utc().format("HH:mm")
  console.log("Looking for users with time: " + now)
  const that = this

  UserStore.getUsersByTime(now, (err, data) => {
    if (err) {
      console.log("Error querying users: " + err)
    } else {
      console.log("Found " + data.Items.length + " users")
      if (data.Items.length > 0) {
        //Pre-warm the cache - otherwise we could send many requests to CAPI before the first response is cached
        const warmups = ["uk", "us", "au", "international"].map((front) => {
          return new Promise(resolve => {
            Capi.getHeadlines("", front, 0, null, resolve)
          })
        })
        Promise.all(warmups).then(result => {
          data.Items.forEach(user => {
            Facebook.getFacebookUser(user.ID, (error, response, body) => {
              if (error) {
                console.log("Error retrieving user " + user.ID + "from facebook: " + JSON.stringify(error))
              } else {
                that.notifyUser(user, JSON.parse(body))
              }
            })
          })
        })
      }
    }
  })
}

Scheduler.prototype.notifyUser = function(dynamoData, fbData) {
  const latestOffset = fbData.timezone
  if (typeof latestOffset !== "undefined") {
    if (latestOffset === dynamoData.offsetHours) {
      Events.morning_briefing(dynamoData)
    } else {
      //User's timezone has changed
      if (latestOffset < dynamoData.offsetHours) {
        //We've missed the notification time for this new timezone, so push now
        Events.morning_briefing(dynamoData)
      }

      const timeStringUTC = getUTCTime(Moment(dynamoData.notificationTime, "HH:mm"), latestOffset)
      UserStore.setNotificationTime(dynamoData.ID, latestOffset, dynamoData.notificationTime, timeStringUTC, (err, data) => {
        if (err) {
          console.log("Error setting notification time for "+ dynamoData.ID +": "+ JSON.stringify(err))
        } else {
          console.log("Updated user's settings: "+ dynamoData.ID)
        }
      })
    }
  } else {
    console.log("No timezone found in facebook response: "+ JSON.stringify(fbData))
    //It appears sometimes FB doesn't give us a timezone, just push now
    Events.morning_briefing(dynamoData)
  }
}
