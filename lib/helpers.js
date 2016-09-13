"use strict"

const Moment = require("moment")

const DEFAULT_IMAGE_URL = process.env.DEFAULT_IMAGE_URL

exports.log = (id, type, event, data) => {
  console.log(JSON.stringify({
    "id": id,
    "event": event,
    "messageType": type,
    "timestamp": Moment.utc().toISOString(),
    "data": data
  }))
}

exports.localeToFront = (locale) => {
  //http://fbdevwiki.com/wiki/Locales
  if (locale === "en_GB") {
    return "uk"
  } else if (locale === "en_US") {
    return "us"
  } else if (locale === "en_UD") {
    return "au"
  } else {
    return "international"
  }
}

exports.frontToUserFriendly = (front) => {
  if (front === "au") {
    return "Australia"
  } else if (front === "international") {
    return "International"
  } else {
    return front.toUpperCase()
  }
}

//Returns the time adjusted by the offset as a string - facebook timezone offset is a float
exports.getUTCTime = (time, offset) => {
  const offsetFloat = parseFloat(offset)
  if (isNaN(offsetFloat)) {
    console.log("Bad timezone offset from facebook: "+ offset)
    return time.format("HH:mm")
  } else {
    const timeUTC = time.subtract(offsetFloat * 60, "minutes")
    return timeUTC.format("HH:mm")
  }
}

// Look for image with width 1000, otherwise the next widest
exports.getImageUrl = (item) => {
  const getMainElement = (elements) => {
    const mainElement = elements.find(element => {
      return ("relation" in element && element.relation === "main")
    })
    if (typeof mainElement !== "undefined") {
      return mainElement
    } else {
      return elements[0]
    }
  }

  let imageUrl = DEFAULT_IMAGE_URL
  if ("elements" in item) {
    const element = getMainElement(item.elements)
    if (typeof element !== "undefined") {
      if ("assets" in element && element.assets.length > 0) {
        element.assets.reduce((widest, asset) => {
          const width = parseInt(asset.typeData.width)
          if (width <= 1000 && width > widest) {
            imageUrl = asset.file
            return width
          } else {
            return widest
          }
        }, 0)
      }
    }
  }
  return imageUrl
}
