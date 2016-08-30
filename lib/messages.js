"use strict"

const Messages = {
  unknown: randomiser([
    "I'm sorry I didn't understand that. I'm only good at simple instructions and sending out headlines at the moment",
    "I'm sorry I didn't understand that. My creators are working hard to make me smarter and more useful for you",
    "I'm sorry I didn't understand that. Typing 'menu' at anytime will bring up the options menu"
  ]),
  unknown_prompt: randomiser([
    "What can you do?"
  ]),
  help: randomiser([
    "I'm a prototype chatbot created by the Guardian to keep you up-to-date with the latest news.\n\nI can give you the headlines, the most popular stories or deliver a morning briefing to you.\n\nHow can I help you today?"
  ]),
  menu: randomiser([
    "How can I help?"
  ]),
  greeting: randomiser([
    "Hi there",
    "Hello",
    "Hi",
    "Hey",
    "Greetings"
  ]),
  welcome: randomiser([
    "I'm a prototype chatbot created by the Guardian to keep you up-to-date with the latest news.\n\nWould you like me to deliver a daily morning briefing to you?"
  ]),
  subscribe_question: randomiser([
    "Would you like to subscribe to the daily morning briefing?"
  ]),
  subscribe_yes: randomiser([
    "Great. When would you like your morning briefing delivered?"
  ]),
  subscribe_no: randomiser([
    "No problem, maybe later then. You can subscribe to the morning briefing at any time from the menu.\n\nWould you like the headlines or the most popular stories?"
  ]),
  subscribed: randomiser([
    "Done. You will start receiving the morning briefing.\n\nRemember, you can change your subscription to this at any time from the menu.\n\nWould you like to see the headlines or the most popular stories right now?"
  ]),
  unsubscribed: randomiser([
    "Done. You will no longer receive the morning briefing.\n\nYou can re-subscribe at any time from the menu"
  ]),
  morning_briefing: randomiser([
    "Good morning! Here are the top stories today",
    "Good morning! Your briefing is ready for you",
    "Good morning! Your briefing has arrived",
    "Good morning! Check out this morning's headline stories"
  ])
}

function randomiser(messages) {
  const r = ms => { return ms[Math.floor(Math.random()*ms.length)] }
  return r.bind(null, messages)
}

exports.Messages = Messages

