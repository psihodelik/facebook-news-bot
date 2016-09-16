"use strict"

module.exports = {
  getHeadlines,
  getMostViewed,
  isTopic
}

const get = require('simple-get-promise').get
const asJson = require('simple-get-promise').asJson
const Moment = require("moment")
const LINK_COUNT = require("./helpers").LINK_COUNT
const MAX_LINKS = require("./helpers").MAX_LINKS

const API_KEY = process.env.API_KEY
const CAPI_BASE_URL = "http://content.guardianapis.com/"

const CACHE_STALE_MILLISECONDS = 30000
const CACHE = new Cache()

function Cache() {
  this._data = {}
}
Cache.prototype.get = function(url) {
  const item = this._data[url]
  if (typeof item !== "undefined" && Moment.utc().diff(item.timestamp) < item.staleMs) {
      return item.data
  } else {
      return null
  }
}
Cache.prototype.put = function(url, data, staleMs) {
  this._data[url] = {
    "timestamp": Moment.utc(),
    "data": data,
    "staleMs": staleMs
  }
}

function isTopic(text) {
  return queryProperties(text) ? true : false
}

function getHeadlines(id, front, offset, topic, callback) {
  const props = getQueryProperties(front, topic)

  if (props) {
    const url = buildUrl(props) + (props.editorsPicks ? "&show-editors-picks=true" : "&page-size="+ MAX_LINKS)
    doCapiQuery(id, url, offset, callback)
  } else {
    console.log(`Error getting headlines - cannot find properties object for ${front} / ${topic} for user ${id}`)
  }
}

function getMostViewed(id, front, offset, topic, callback) {
  const props = getQueryProperties(front, topic)
  if (props) {
    const url = buildUrl(props) + (props.mostViewed ? "&show-most-viewed=true" : "&page-size="+ MAX_LINKS)
    doCapiQuery(id, url, offset, callback)
  } else {
    console.log(`Error getting most viewed - cannot find properties object for ${front} / ${topic} for user ${id}`)
  }
}

function buildUrl(props) {
  return CAPI_BASE_URL + props.path + "?"
    + "api-key=" + API_KEY
    + "&show-fields=standfirst,thumbnail"
    + "&tags=type/article,-tone/minutebyminute&show-elements=image"
    + (props.toneNews ? ",tone/news" : "")
}

function doCapiQuery(id, url, offset, callback) {
  const cached = CACHE.get(url)
  if (cached) {
    callback(getPageFromResults(cached, offset))
  } else {
    get(url)
      .then(asJson)
      .then(json => {
        //Cache all MAX_LINKS results, then take the LINK_COUNT results
        const data = getResults(json.response)
        CACHE.put(url, data, CACHE_STALE_MILLISECONDS)
        callback(getPageFromResults(data, offset))
      })
      .catch(error => { console.log("CAPI error: "+ error) })
  }
}

const getPageFromResults = (results, offset) => results.slice(offset,offset+LINK_COUNT)

const getResults = (response) => {
  if (response["editorsPicks"]) return response["editorsPicks"]
  else if (response["mostViewed"]) return response["mostViewed"]
  else return response["results"]
}

const getQueryProperties = (front, topic) => {
  const props = queryProperties(topic ? topic : front)
  if (props !== null) {
    if (props[front]) return props[front]
    else return props.default
  } else return null
}

/**
 * Get the properties object for a topic. This defines how to build the capi query.
 * Each properties object can contain multiple objects for different editions, but it must have a 'default'
 * object.
 */
const queryProperties = (topic) => {
  switch (topic.toLowerCase()) {
    case "united kingdom":
    case "uk": return {
      default: {
        toneNews: true,       //tags=tone/news
        editorsPicks: true,   //show-editors-picks
        mostViewed: true,     //show-most-viewed
        path: "uk"
      }
    };
    case "united states":
    case "usa":
    case "us": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "us"
      }
    };
    case "australia":
    case "au": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "au"
      }
    };
    case "international": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "international"
      }
    };
    case "world": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "world"
      }
    };
    case "politics": return {
      default: {
        //UK politics
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "politics"
      },
      us: {
        toneNews: true,
        editorsPicks: false,
        mostViewed: false,
        path: "us-news/us-politics"
      },
      au: {
        toneNews: false,
        editorsPicks: false,
        mostViewed: false,
        path: "australia-news/australian-politics"
      }
    };
    case "sport": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "uk/sport"
      },
      us: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "us/sport"
      },
      au: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "au/sport"
      }
    };
    case "us sports":
    case "us sport": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "us/sport"
      }
    };
    case "football":
    case "soccer": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "football"
      }
    };
    case "cricket": return {
      default: {
        toneNews: true,
        editorsPicks: false,
        mostViewed: false,
        path: "sport/cricket"
      }
    };
    case "rugby":
    case "rugby union": return {
      default: {
        toneNews: true,
        editorsPicks: false,
        mostViewed: false,
        path: "sport/rugby-union"
      }
    };
    case "formula 1":
    case "f1": return {
      default: {
        toneNews: true,
        editorsPicks: false,
        mostViewed: false,
        path: "sport/formulaone"
      }
    };
    case "tennis": return {
      default: {
        toneNews: true,
        editorsPicks: false,
        mostViewed: false,
        path: "sport/tennis"
      }
    };
    case "golf": return {
      default: {
        toneNews: true,
        editorsPicks: false,
        mostViewed: false,
        path: "sport/golf"
      }
    };
    case "cycling": return {
      default: {
        toneNews: true,
        editorsPicks: false,
        mostViewed: false,
        path: "sport/cycling"
      }
    };
    case "boxing": return {
      default: {
        toneNews: true,
        editorsPicks: false,
        mostViewed: false,
        path: "sport/boxing"
      }
    };
    case "horse racing": return {
      default: {
        toneNews: true,
        editorsPicks: false,
        mostViewed: false,
        path: "sport/horse-racing"
      }
    };
    case "rugby league": return {
      default: {
        toneNews: true,
        editorsPicks: false,
        mostViewed: false,
        path: "sport/rugbyleague"
      }
    };
    case "film":
    case "movies": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "uk/film"
      },
      us: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "us/film"
      },
      au: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "au/film"
      }
    };
    case "tv":
    case "radio":
    case "tv and radio": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "tv-and-radio"
      }
    };
    case "music": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "music"
      }
    };
    case "games": return {
      default: {
        toneNews: false,
        editorsPicks: false,
        mostViewed: false,
        path: "technology/games"
      }
    };
    case "books": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "books"
      }
    };
    case "art": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "artanddesign"
      }
    };
    case "stage": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "stage"
      }
    };
    case "business": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "uk/business"
      },
      us: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "us/business"
      },
      au: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "au/business"
      }
    };
    case "life and style":
    case "lifestyle": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "uk/lifeandstyle"
      },
      us: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "us/lifeandstyle"
      },
      au: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "au/lifeandstyle"
      }
    };
    case "food": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: false,
        path: "lifeandstyle/food-and-drink"
      }
    };
    case "health":
    case "fitness":
    case "wellbeing": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: false,
        path: "lifeandstyle/health-and-wellbeing"
      }
    };
    case "family": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: false,
        path: "lifeandstyle/family"
      }
    };
    case "women": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: false,
        path: "lifeandstyle/women"
      }
    };
    case "fashion": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "fashion"
      }
    };
    case "environment": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "uk/environment"
      },
      us: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "us/environment"
      },
      au: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "au/environment"
      }
    };
    case "climate change": return {
      default: {
        toneNews: true,
        editorsPicks: false,
        mostViewed: false,
        path: "environment/climate-change"
      }
    };
    case "technology":
    case "tech": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "uk/technology"
      },
      us: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "us/technology"
      }
    };
    case "travel": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "uk/travel"
      },
      us: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "us/travel"
      },
      au: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "au/travel"
      }
    };
    case "culture": return {
      default: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "uk/culture"
      },
      us: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "us/culture"
      },
      au: {
        toneNews: false,
        editorsPicks: true,
        mostViewed: true,
        path: "au/culture"
      }
    };
    case "science": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "science"
      }
    };
    case "money": return {
      default: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "uk/money"
      },
      us: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "us/money"
      },
      au: {
        toneNews: true,
        editorsPicks: true,
        mostViewed: true,
        path: "au/money"
      }
    };

    default: return null;
  }
};
