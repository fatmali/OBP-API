package code.api.v1_4_0

import code.api.DefaultUsers
import code.api.test.{APIResponse, ServerSetupWithTestData, ServerSetup}
import code.api.util.APIUtil.OAuth.{Token, Consumer}
import code.api.v1_2_1.{TransactionsJSON, TransactionJSON, MakePaymentJson}
import code.api.v1_4_0.JSONFactory1_4_0._
import code.bankconnectors.Connector
import code.model.{TransactionRequestId, AccountId, BankAccount}
import code.transactionrequests.TransactionRequests
import code.api.util.APIUtil.OAuth._
import dispatch._
import net.liftweb.json.JsonAST.JString
import net.liftweb.json._
import net.liftweb.util.Props
import org.scalatest.Tag
import java.util.Calendar
import net.liftweb.json.Serialization.{read, write}

class TransactionRequestsTest extends ServerSetupWithTestData with DefaultUsers with V140ServerSetup {

  object TransactionRequest extends Tag("transactionRequests")

  feature("we can make transaction requests") {
    val view = "owner"

    def transactionCount(accounts: BankAccount*) : Int = {
      accounts.foldLeft(0)((accumulator, account) => {
        //TODO: might be nice to avoid direct use of the connector, but if we use an api call we need to do
        //it with the correct account owners, and be sure that we don't even run into pagination problems
        accumulator + Connector.connector.vend.getTransactions(account.bankId, account.accountId).get.size
      })
    }

    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request without challenge", TransactionRequest) {}
    } else {
      scenario("we create a transaction request without challenge", TransactionRequest) {
        val testBank = createBank("transactions-test-bank")
        val bankId = testBank.bankId
        val accountId1 = AccountId("__acc1")
        val accountId2 = AccountId("__acc2")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

        def getFromAccount: BankAccount = {
          BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
        }

        def getToAccount: BankAccount = {
          BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
        }

        val fromAccount = getFromAccount
        val toAccount = getToAccount

        val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

        val beforeFromBalance = fromAccount.balance
        val beforeToBalance = toAccount.balance

        //Create a transaction (request)
        //1. get possible challenge types for from account
        //2. create transaction request to to-account with one of the possible challenges
        //3. answer challenge
        //4. have a new transaction

        val transactionRequestId = TransactionRequestId("__trans1")
        val toAccountJson = TransactionRequestAccountJSON(toAccount.bankId.value, toAccount.accountId.value)

        val amt = BigDecimal("12.50")
        val bodyValue = AmountOfMoneyJSON("EUR", amt.toString())
        val transactionRequestBody = TransactionRequestBodyJSON(toAccountJson, bodyValue, "Test Transaction Request description", "")

        //call createTransactionRequest
        var request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
                        "owner" / "transaction-request-types" / "SANDBOX" / "transaction-requests").POST <@(user1)
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 201 created code")
        response.code should equal(201)

        //created a transaction request, check some return values. As type is SANDBOX, we expect no challenge
        val transId: String = (response.body \ "transactionRequestId" \ "value") match {
          case JString(i) => i
          case _ => ""
        }
        Then("We should have some new transaction id")
        transId should not equal ("")

        val status: String = (response.body \ "status") match {
          case JString(i) => i
          case _ => ""
        }
        status should equal (code.transactionrequests.TransactionRequests.STATUS_COMPLETED)

        var challenge = (response.body \ "challenge").children
        challenge.size should equal(0)

        var transaction_id = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case _ => ""
        }
        transaction_id should not equal("")

        //call getTransactionRequests, check that we really created a transaction request
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
                    "owner" / "transaction-requests").GET <@(user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)
        val transactionRequests = response.body.children
        transactionRequests.size should not equal(0)

        //check transaction_ids again
        transaction_id = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case _ => ""
        }
        transaction_id should not equal("")

        //make sure that we also get no challenges back from this url (after getting from db)
        challenge = (response.body \ "challenge").children
        challenge.size should equal(0)

        //check that we created a new transaction (since no challenge)
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transactions").GET <@(user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)
        val transactions = response.body.children

        transactions.size should equal(1)

        //check that the description has been set
        val description = (((response.body \ "transactions")(0) \ "details") \ "description") match {
          case JString(i) => i
          case _ => ""
        }
        description should not equal ("")

        //TODO: check that the balances have been properly decreased/increased (since we handle that logic for sandbox accounts at least)
        //(do it here even though the payments test does test makePayment already)

/*      val fromAccountTransAmt = transJson.details.value.amount
        //the from account transaction should have a negative value
        //since money left the account
        And("the json we receive back should have a transaction amount equal to the amount specified to pay")
        fromAccountTransAmt should equal((-amt).toString)

        val expectedNewFromBalance = beforeFromBalance - amt
        And("the account sending the payment should have a new_balance amount equal to the previous balance minus the amount paid")
        transJson.details.new_balance.amount should equal(expectedNewFromBalance.toString)
        getFromAccount.balance should equal(expectedNewFromBalance)
        val toAccountTransactionsReq = getTransactions(toAccount.bankId.value, toAccount.accountId.value, view, user1)
        toAccountTransactionsReq.code should equal(200)
        val toAccountTransactions = toAccountTransactionsReq.body.extract[TransactionsJSON]
        val newestToAccountTransaction = toAccountTransactions.transactions(0)

        //here amt should be positive (unlike in the transaction in the "from" account")
        And("the newest transaction for the account receiving the payment should have the proper amount")
        newestToAccountTransaction.details.value.amount should equal(amt.toString)

        And("the account receiving the payment should have the proper balance")
        val expectedNewToBalance = beforeToBalance + amt
        newestToAccountTransaction.details.new_balance.amount should equal(expectedNewToBalance.toString)
        getToAccount.balance should equal(expectedNewToBalance)

        And("there should now be 2 new transactions in the database (one for the sender, one for the receiver")
        transactionCount(fromAccount, toAccount) should equal(totalTransactionsBefore + 2)
        */
      }
    }

    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request with a challenge", TransactionRequest) {}
    } else {
      scenario("we create a transaction request with a challenge", TransactionRequest) {
        //setup accounts
        val testBank = createBank("transactions-test-bank")
        val bankId = testBank.bankId
        val accountId1 = AccountId("__acc1")
        val accountId2 = AccountId("__acc2")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

        def getFromAccount: BankAccount = {
          BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
        }

        def getToAccount: BankAccount = {
          BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
        }

        val fromAccount = getFromAccount
        val toAccount = getToAccount

        val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

        val beforeFromBalance = fromAccount.balance
        val beforeToBalance = toAccount.balance

        val transactionRequestId = TransactionRequestId("__trans1")
        val toAccountJson = TransactionRequestAccountJSON(toAccount.bankId.value, toAccount.accountId.value)

        //1. TODO: get possible challenge types from account

        //2. create transaction request to to-account with one of the possible challenges

        //amount over 100 €, so should trigger challenge request
        val amt = BigDecimal("1250.00")
        val bodyValue = AmountOfMoneyJSON("EUR", amt.toString())
        val transactionRequestBody = TransactionRequestBodyJSON(toAccountJson, bodyValue, "Test Transaction Request description", TransactionRequests.CHALLENGE_SANDBOX_TAN)

        //call createTransactionRequest API method
        var request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / "SANDBOX" / "transaction-requests").POST <@ (user1)
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 201 created code")
        response.code should equal(201)

        //ok, created a transaction request, check some return values. As type is SANDBOX but over 100€, we expect a challenge
        val transId: String = (response.body \ "transactionRequestId" \ "value") match {
          case JString(i) => i
          case _ => ""
        }
        transId should not equal ("")

        var status: String = (response.body \ "status") match {
          case JString(i) => i
          case _ => ""
        }
        status should equal(code.transactionrequests.TransactionRequests.STATUS_INITIATED)

        var transaction_id = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case _ => ""
        }
        transaction_id should equal ("")

        var challenge = (response.body \ "challenge").children
        challenge.size should not equal(0)

        val challenge_id = (response.body \ "challenge" \ "id") match {
          case JString(s) => s
          case _ => ""
        }
        challenge_id should not equal("")

        //call getTransactionRequests, check that we really created a transaction request
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-requests").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)
        var transactionRequests = response.body.children

        transactionRequests.size should equal(1)
        transaction_id = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case _ => ""
        }
        transaction_id should equal ("")

        challenge = (response.body \ "challenge").children
        challenge.size should not equal(0)

        //3. answer challenge and check if transaction is being created
        //call answerTransactionRequestChallenge, give a false answer
        var answerJson = ChallengeAnswerJSON(id = challenge_id, answer = "hello") //wrong answer, not a number
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / "sandbox" / "transaction-requests" / transId / "challenge").POST <@ (user1)
        response = makePostRequest(request, write(answerJson))
        Then("we should get a 400 bad request code")
        response.code should equal(400)

        //TODO: check if allowed_attempts is decreased

        //call answerTransactionRequestChallenge again, give a good answer
        answerJson = ChallengeAnswerJSON(id = challenge_id, answer = "12345") //wrong answer, not a number
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / "sandbox" / "transaction-requests" / transId / "challenge").POST <@ (user1)
        response = makePostRequest(request, write(answerJson))
        Then("we should get a 202 accepted code")
        response.code should equal(202)

        //check if returned data includes new transaction's id
        status = (response.body \ "status") match {
          case JString(i) => i
          case _ => ""
        }
        status should equal(code.transactionrequests.TransactionRequests.STATUS_COMPLETED)

        transaction_id = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case _ => ""
        }
        transaction_id should not equal ("")

        //call getTransactionRequests, check that we really created a transaction
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-requests").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)
        transactionRequests = response.body.children

        transactionRequests.size should equal(1)
        transaction_id = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case _ => ""
        }
        transaction_id should not equal ("")

        challenge = (response.body \ "challenge").children
        challenge.size should not equal(0)
      }
    }

    /*
    scenario("we can't make a payment without access to the owner view", Payments) {
      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId

      val accountId1 = AccountId("__acc1")
      val accountId2 = AccountId("__acc2")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount : BankAccount = {
        BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      val fromAccount = getFromAccount
      val toAccount = getToAccount

      val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      val beforeFromBalance = fromAccount.balance
      val beforeToBalance = toAccount.balance

      val amt = BigDecimal("12.33")

      val payJson = MakePaymentJson(toAccount.bankId.value, toAccount.accountId.value, amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, user2)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for each account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount, toAccount))

      And("the balances of each account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
      beforeToBalance should equal(getToAccount.balance)
    }

    scenario("we can't make a payment without an oauth user", Payments) {
      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1")
      val accountId2 = AccountId("__acc2")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount : BankAccount = {
        BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      val fromAccount = getFromAccount
      val toAccount = getToAccount

      val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      val beforeFromBalance = fromAccount.balance
      val beforeToBalance = toAccount.balance

      val amt = BigDecimal("12.33")

      val payJson = MakePaymentJson(toAccount.bankId.value, toAccount.accountId.value, amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, None)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for each account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount, toAccount))

      And("the balances of each account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
      beforeToBalance should equal(getToAccount.balance)
    }

    scenario("we can't make a payment of zero units of currency", Payments) {
      When("we try to make a payment with amount = 0")

      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1")
      val accountId2 = AccountId("__acc2")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount : BankAccount = {
        BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      val fromAccount = getFromAccount
      val toAccount = getToAccount

      val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      val beforeFromBalance = fromAccount.balance
      val beforeToBalance = toAccount.balance

      val amt = BigDecimal("0")

      val payJson = MakePaymentJson(toAccount.bankId.value, toAccount.accountId.value, amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, user1)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for each account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount, toAccount))

      And("the balances of each account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
      beforeToBalance should equal(getToAccount.balance)
    }

    scenario("we can't make a payment with a negative amount of money", Payments) {

      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1")
      val accountId2 = AccountId("__acc2")
      val acc1 = createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
      val acc2  = createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

      When("we try to make a payment with amount < 0")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount : BankAccount = {
        BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      val fromAccount = getFromAccount
      val toAccount = getToAccount

      val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      val beforeFromBalance = fromAccount.balance
      val beforeToBalance = toAccount.balance

      val amt = BigDecimal("-20.30")

      val payJson = MakePaymentJson(toAccount.bankId.value, toAccount.accountId.value, amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, user1)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for each account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount, toAccount))

      And("the balances of each account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
      beforeToBalance should equal(getToAccount.balance)
    }

    scenario("we can't make a payment to an account that doesn't exist", Payments) {

      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1")
      val acc1 = createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")

      When("we try to make a payment to an account that doesn't exist")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      val fromAccount = getFromAccount

      val totalTransactionsBefore = transactionCount(fromAccount)

      val beforeFromBalance = fromAccount.balance

      val amt = BigDecimal("17.30")

      val payJson = MakePaymentJson(bankId.value, "ACCOUNTTHATDOESNOTEXIST232321321", amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, user1)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for the sender's account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount))

      And("the balance of the sender's account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
    }

    scenario("we can't make a payment between accounts with different currencies", Payments) {
      When("we try to make a payment to an account that has a different currency")
      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1")
      val accountId2 = AccountId("__acc2")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "GBP")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount : BankAccount = {
        BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      val fromAccount = getFromAccount
      val toAccount = getToAccount

      val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      val beforeFromBalance = fromAccount.balance
      val beforeToBalance = toAccount.balance

      val amt = BigDecimal("4.95")

      val payJson = MakePaymentJson(toAccount.bankId.value, toAccount.accountId.value, amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, user1)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for each account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount, toAccount))

      And("the balances of each account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
      beforeToBalance should equal(getToAccount.balance)
    } */
  }
}