# VIVO to JOAI connector

## Build
```mvn install```

## Run
```java -jar target/joai_connector.jar -url https://example.com -data ~/joai-data/ ```

## Options
- data \<arg\>     Mandatory, path to result data directory
- url \<arg\>      Mandatory, Vitro/VIVO instance URL
- config \<arg\>   Optional, configuration path to provide parameters in properties file.
- user \<arg\>     Optional, Vitro/VIVO user email
- pass \<arg\>     Optional, Vitro/VIVO password
 
## Description

Client application to harvest VIVO metadata and save it as XML files suitable to be used as a source format for [JOAI](https://github.com/NCAR/joai-project/).
Application requests configuration from `/api/dataRequest/OAI PMH Configuration` and downloads metadata records for each collection.

## Supported configuration parameters
- directory - directory name for collection records
- filter - filter to be used to harvest collection records. Multiple filters for each collection are supported.
- lang - preferred language (optional) metadta requests
- endpoint - name of endpoint for metadata request
- suffix - file name suffix for metadata record
