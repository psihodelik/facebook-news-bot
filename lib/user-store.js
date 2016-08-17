"use strict"

module.exports = UserStore

function UserStore() {
  //TODO - dynamodb
  this._users = {}
}

UserStore.prototype.userExists = function(id) {
  if (id in this._users) {
    return true
  } else {
    return false
  }
}

UserStore.prototype.addUser = function(id) {
  this._users[id] = 1
}

UserStore.prototype.setNotificationTime = function(id, time) {
  this._users[id] = time
}
