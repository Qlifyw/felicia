[![All Contributors](https://img.shields.io/badge/all_contributors-2-orange.svg?style=flat-square)](#Contributors) 
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](/LICENSE)
# Felicia

`Felicia` is an application that allows your to receive a message from from `kafka topics` through HTTP request.

## Prerequisites

Before you begin, ensure you have met the following requirements:
* You have installed the `java Runtime Environment(JRE)`

## Build with 
* [Ktor.io](https://ktor.io/ "Ktor.io")
* [Coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html "Coroutines")
* [Gradle](https://gradle.org/ "Gradle")

## Launch

To run `felicia`, follow these steps:

Linux, macOS and Windows:
```
java -jar /path/to/app.jar
```
## REST API

### 1. Subscribe

#### Request

`POST /kafka/subscribe`

#####  Headers

| Key | Description | 
| --- | --- |
| hosts | Kafka hosts (ex:  <i>10.0.20.114:9092</i> ) 
| group_id | Group id for consumer 
| topics | Kafka topics you want to subscribe | 
| login | credentials (optional) | 
| password | credentials (optional) | 


```curl
curl --location --request POST 'http://host:port/kafka/subscribe' \
--header 'Content-Type: application/json' \
--header 'hosts: 10.0.20.114:9092' \
--header 'group_id: my-group-id' \
--header 'topics: target-topic' \
--data-raw ''
```

### 2. Find

#### Request

`POST /kafka/find`

#####  Query Params

| key | Description |
| --- | --- |
| pattern | Any part of message content from topic |

#####  Headers

| Key | Description |
| --- | --- |
| topic | Topic name in which you need to find a message |
| timeout | Timeout for search, seconds |

```curl
curl --location --request POST 'http://host:port//kafka/find?pattern=id' \
--header 'Content-Type: application/json' \
--header 'topic: target-topic' \
--header 'timeout: 2' \
--data-raw ''
```

## Contributors

<table>
  <tr>
    <td align="center"><a href="https://github.com/Qlifyw"><img src="https://avatars0.githubusercontent.com/u/22730194?s=460&u=f44ef45a95e557c3c8bede335873860096181ca1&v=4" width="100px;" alt=""/><br /><sub><b>qlifyw</b></sub></a><br /><a href="#" title="Code">💻</a><a href="#" title="Maintenance">🚧</a></td>
    <td align="center"><a href="https://github.com/unwnu"><img src="https://avatars0.githubusercontent.com/u/52795077?s=400&u=c952b778f671b0e116be426d6c3e54a21d2955b6&v=4" width="100px;" alt=""/><br /><sub><b>unwnu</b></sub></a><br /><a href="#" title="Documentation">📖</a> <a href="#" title="Ideas, Planning, & Feedback">🤔</a> <a href="#" title="Testing">🔧</a></td>
  </tr>
</table>
