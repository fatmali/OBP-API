/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v3_1_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v2_1_0.ConsumersJson
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class ConsumerTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.getConsumer))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getConsumersForCurrentUser))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.getConsumers))
  feature("Get Consumer by CONSUMER_ID - v3.1.0")
  {
    scenario("We will Get Consumer by CONSUMER_ID without a proper Role " + canGetConsumers, ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + canGetConsumers)
      val request310 = (v3_1_0_Request / "management" / "consumers" / "non existing CONSUMER_ID").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetConsumers)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetConsumers)
    }
    scenario("We will Get Consumer by CONSUMER_ID with a proper Role " + canGetConsumers, ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetConsumers.toString)
      When("We make a request v3.1.0")
      val consumerId = "non existing CONSUMER_ID"
      val request310 = (v3_1_0_Request / "management" / "consumers" / consumerId).GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 404")
      response310.code should equal(404)
      val errorMessage = s"$ConsumerNotFoundByConsumerId Current ConsumerId is $consumerId"
      And("error should be " + errorMessage)
      response310.body.extract[ErrorMessage].message should equal (errorMessage)
    }
  }
  feature("Get Consumers for current user - v3.1.0")
  {
    scenario("We will Get Consumers for current user - NOT logged in", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "users" / "current" / "consumers").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will Get Consumers for current user", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "users" / "current" / "consumers").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[ConsumersJsonV310]
    }
  }
  feature("Get Consumers - v3.1.0")
  {
    scenario("We will Get Consumers - User NOT logged in", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "consumers").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will Get Consumers without a proper Role " + canGetConsumers, ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + canGetConsumers)
      val request310 = (v3_1_0_Request / "management" / "consumers").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetConsumers)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetConsumers)
    }
    scenario("We will Get Consumers with a proper Role " + canGetConsumers, ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetConsumers.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "consumers").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[ConsumersJson]
    }
  }
}
