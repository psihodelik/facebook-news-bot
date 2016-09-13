const Moment = require("moment")
const tap = require("tap")

const getUTCTime = require("../lib/helpers").getUTCTime
tap.equal(getUTCTime(Moment("06:00", "HH:mm"), 0), "06:00")
tap.equal(getUTCTime(Moment("06:00", "HH:mm"), 1), "05:00")
tap.equal(getUTCTime(Moment("06:00", "HH:mm"), -1), "07:00")
tap.equal(getUTCTime(Moment("06:00", "HH:mm"), 0.75), "05:15")
tap.equal(getUTCTime(Moment("06:00", "HH:mm"), -0.75), "06:45")
