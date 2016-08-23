"use strict"

const AWS = require("aws-sdk")

const TABLE_NAME = process.env.DYNAMODB_TABLE_NAME

AWS.config.update({
  region: 'eu-west-1'
})

module.exports = UserStore

function UserStore() {
  this._docClient = new AWS.DynamoDB.DocumentClient()
}

UserStore.prototype.getUser = function(id, callback) {
  this._docClient.get({
    TableName: TABLE_NAME,
    Key: {
      "ID": id
    }
  }, callback)
}

UserStore.prototype.addUser = function(id, front, callback) {
  this._docClient.put({
    TableName: TABLE_NAME,
    Item: {
      "ID": id,
      "offsetHours": 0,
      "notificationTime": "-",
      "notificationTimeUTC": "-",
      "front": front
    }
  }, callback)
}

UserStore.prototype.setNotificationTime = function(id, offset, time, timeUTC, callback) {
  this._docClient.update({
    TableName: TABLE_NAME,
    Key: {
      "ID": id
    },
    UpdateExpression: "set notificationTimeUTC = :tUTC, notificationTime = :t, offsetHours = :o",
    ExpressionAttributeValues: {
      ":t": time,
      ":tUTC": timeUTC,
      ":o": offset
    }
  }, callback)
}

UserStore.prototype.setFront = function(id, front, callback) {
  this._docClient.update({
    TableName: TABLE_NAME,
    Key: {
      "ID": id
    },
    UpdateExpression: "set front = :f",
    ExpressionAttributeValues: {
      ":f": front
    }
  }, callback)
}

UserStore.prototype.notifyUsers = function(time, callback) {
  this._docClient.query({
    TableName: TABLE_NAME,
    IndexName: "notificationTimeUTC-ID-index",
    KeyConditionExpression: "notificationTimeUTC = :t",
    ExpressionAttributeValues: {
      ":t": time
    },
  }, callback)
}

UserStore.prototype.unsubscribe = function(id, callback) {
  this._docClient.update({
    TableName: TABLE_NAME,
    Key: {
      "ID": id
    },
    UpdateExpression: "set notificationTimeUTC = :tUTC, notificationTime = :t",
    ExpressionAttributeValues: {
      ":t": "-",
      ":tUTC": "-"
    }
  }, callback)
}

