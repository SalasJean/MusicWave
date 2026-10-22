Feature: Black-box regression for download ingestion and lifecycle controls

  Background:
    * url apiBaseUrl
    * def runId = java.util.UUID.randomUUID().toString()
    * def idempotencyKey = 'karate-' + runId
    * def sourceUrl = 'https://example.com/musicwave-qa/' + runId

  Scenario: Create, replay, query and cancel one pending job
    Given path 'api', 'v1', 'downloads'
    And header Idempotency-Key = idempotencyKey
    And request { url: '#(sourceUrl)' }
    When method post
    Then status 201
    And match response.replayed == false
    And match response.createdCount == 1
    And match response.jobs[0].status == 'PENDING'
    * def jobId = response.jobs[0].id
    * def requestId = response.requestId

    Given path 'api', 'v1', 'downloads'
    And header Idempotency-Key = idempotencyKey
    And request { url: '#(sourceUrl)' }
    When method post
    Then status 200
    And match response.replayed == true
    And match response.requestId == requestId
    And match response.jobs[0].id == jobId

    Given path 'api', 'v1', 'downloads'
    And header Idempotency-Key = idempotencyKey
    And request { url: 'https://example.org/different-payload' }
    When method post
    Then status 409
    And match response.code == 'IDEMPOTENCY_KEY_REUSED'
    And match response.status == 409

    Given path 'api', 'v1', 'downloads', jobId
    When method get
    Then status 200
    And match response.id == jobId
    And match response.status == 'PENDING'

    Given path 'api', 'v1', 'downloads', jobId, 'artifact'
    When method get
    Then status 409
    And match response.code == 'ARTIFACT_NOT_READY'

    Given path 'api', 'v1', 'downloads', jobId, 'cancel'
    When method post
    Then status 200
    And match response.id == jobId
    And match response.status == 'CANCELLED'

  Scenario: Reject a non-HTTP source as an RFC problem response
    Given path 'api', 'v1', 'downloads'
    And request { url: 'ftp://example.com/audio.mp3' }
    When method post
    Then status 400
    And match response.status == 400
    And match response.code == 'URL_SCHEME_NOT_ALLOWED'
    And match response.title == '#string'
    And match response.detail == '#string'
