```
+----------------------------------+
|          INTERACTIVES            |                      +--------------------+          +---------------+
|                                  |                      |                    |          |               |
|                                  |                      |                    |          |               |
| +------------------------------+ |                      |                    |*        1|               |
| |                              | |          +-----------> USER_TEAM dynamodb +----------+ USER dynamodb <---------------------+
| | football-transfers-publisher | |          |           |                    |          |               |                     |
| |                              | |          |           |                    |          |               |                     |
| +--------------+---------------+ |          |           |                    |          |               |                     |
|                |                 |          |           +--------------------+          +-------^-------+                     |
|                |                 |          |                                                   |                             |
|           +-+-+v+-+-+            |          |                                                   |                             |
|           | | | | | |            |          |                               +-------------------+---------+  +----------------+-------------------+
|           +-+-+^+-+-+            |          |                               |                             |  |                                    |
|                |                 |          |                               | facebook-news-bot-scheduler |  | facebook-news-bot-football-rumours |
+----------------------------------+          |                               |                             |  |                                    |
                 |                            |                               +-------+---------------------+  +----------------+-------------------+
                 |          +-----------------+--------------------+                  |                                         |
                 |          |                                      |                  |                                         |
                 +----------+ facebook-news-bot-football-transfers |             +-+-+v+-+-+                               +-+-+v+-+-+
                            |                                      |             | | | | | |                               | | | | | |
                            +-----------------+--------------------+             +-+-+^+-+-+                               +-+-+^+-+-+
                                              |                                       |                                         |
                                              |                                       |                                         |
                                         +-+-+v+-+-+                   +--------------+------+                                  |
                                         | | | | | |                   |                     |                                  |
                                         +-+-+^+-+-+                   |                     |                                  |
                                              |                        |  facebook-news-bot  |                                  |
                                              +------------------------+                     +----------------------------------+
                                                                       |                     |
                                                                       +---------------------+
```