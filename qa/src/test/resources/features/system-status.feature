Feature: MusicWave operational API preflight

  Background:
    * url apiBaseUrl

  Scenario: API health is available
    Given path 'actuator', 'health'
    When method get
    Then status 200
    And match response.status == 'UP'

  Scenario: Public system status exposes the service identity
    Given path 'api', 'v1', 'system', 'status'
    When method get
    Then status 200
    And match response == '#object'

  Scenario: At least one worker reports ready media tools
    Given path 'api', 'v1', 'tools', 'status'
    When method get
    Then status 200
    And match response.status == 'READY'
    And match response.workers == '#[1]'
