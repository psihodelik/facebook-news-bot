"use strict"

const AWS = require("aws-sdk")

const TABLE_NAME = process.env.DYNAMODB_TABLE_NAME

AWS.config.update({
  region: 'eu-west-1'
})

const docClient = new AWS.DynamoDB.DocumentClient()

exports.getUser = (id, callback) => {
  docClient.get({
    TableName: TABLE_NAME,
    Key: {
      "ID": id
    }
  }, callback)
}

exports.addUser = (id, front, callback) => {
  docClient.put({
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

exports.setNotificationTime = (id, offset, time, timeUTC, callback) => {
  docClient.update({
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

exports.setFront = (id, front, callback) => {
  docClient.update({
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

exports.getUsersByTime = (time, callback) => {
  docClient.query({
    TableName: TABLE_NAME,
    IndexName: "notificationTimeUTC-ID-index",
    KeyConditionExpression: "notificationTimeUTC = :t",
    ExpressionAttributeValues: {
      ":t": time
    },
  }, callback)
}

exports.unsubscribe = (id, callback) => {
  docClient.update({
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
