package main

import auctionsystem._
import com.daml.ledger.javaapi.data.CreatedEvent
import com.daml.ledger.javaapi.data.Filter
import com.daml.ledger.javaapi.data.FiltersByParty
import com.daml.ledger.javaapi.data.GetUserRequest
import com.daml.ledger.javaapi.data.LedgerOffset
import com.daml.ledger.javaapi.data.NoFilter
import com.daml.ledger.javaapi.data.Value
import com.daml.ledger.rxjava.DamlLedgerClient
import io.reactivex.Flowable
import java.util.Collections
import java.util.UUID
import scala.io.StdIn.readLine
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

sealed trait SellerState
case object Initial extends SellerState
case object BiddersInvited extends SellerState

sealed trait BuyerState
final case object WaitingForInvitation extends BuyerState
final case class InvitationAccepted(invitation: AuctionInvitation) extends BuyerState
final case object AuctionTerminated extends BuyerState

object Main {

  val appId = "AuctionSystem"
  val aliceUser = "alice"
  val bobUser = "bob"
  val sellerUser = "seller"

  val host = "localhost"
  val port = 7600

  val client = DamlLedgerClient.newBuilder(host, port).build();
  client.connect()
  val userManagementClient = client.getUserManagementClient()

  val aliceParty = userManagementClient.getUser(new GetUserRequest(aliceUser)).blockingGet().getUser().getPrimaryParty().get();
  val bobParty = userManagementClient.getUser(new GetUserRequest(bobUser)).blockingGet().getUser().getPrimaryParty().get();
  val sellerParty = userManagementClient.getUser(new GetUserRequest(sellerUser)).blockingGet().getUser().getPrimaryParty().get()

  def startAuction(auction: Auction) = 
    client.getCommandClient().submitAndWait(
      "auction-creation",
      appId,
      UUID.randomUUID().toString(),
      sellerParty,
      Collections.singletonList(auction.create())
    ).blockingGet()
  
  def inviteBidders(auction: Auction) = {
    Seq(aliceParty, bobParty).foreach { party =>
      client.getCommandClient().submitAndWait(
        s"auctionInvitation-$party",
        appId,
        UUID.randomUUID().toString(),
        sellerParty,
        Collections.singletonList(auction.createAndExerciseInviteBidder(party))
      ).blockingGet()
    }
  }

  def terminateAuction(auction: Auction) = {
    val map = new java.util.HashMap[String, Filter]()
    map.put(sellerParty, NoFilter.instance)

    val bids = client.getActiveContractSetClient().getActiveContracts(
      new FiltersByParty(map),
      true
    ).flatMap { tx =>
      Flowable.fromArray(
        tx.getCreatedEvents().asScala
          .map(event => (event.getContractId(), Try(Bid.fromValue(event.getArguments()))))
          .collect { case (cid, Success(_)) => new Bid.ContractId(cid) }
          .toList: _*
      )
    }.toList()
     .blockingGet()

    val auctionResult = client.getCommandClient().submitAndWaitForTransaction(
      "obtaining-the-winner",
        appId,
        UUID.randomUUID().toString(),
        sellerParty,
        Collections.singletonList(auction.createAndExerciseEndAuction(bids))
    ).toFlowable().flatMap(transaction =>
      Flowable.fromArray(
        transaction.getEvents().asScala
          .collect{ case e: CreatedEvent => e.getArguments() }
          .map(record => Try(AuctionResult.fromValue(record)))
          .collect { case Success(value) => value }
          .toList : _*
      )
    ).blockingFirst()

    val auctionName = auctionResult.auction.description
    println {
      auctionResult.winningBid.toScala
        .map(bid => s"Auction '$auctionName' was won by '${bid.offer.party}' with price '${bid.offer.price}'")
        .getOrElse(s"Auction '$auctionName' has no winner.")
    }
  }

  def runSeller(auctionDesc: String) = {
    val auction = new Auction(sellerParty, auctionDesc)

    def options(st: SellerState): Unit = {
      println("Options:")
      val inviteBidders = "'i': invite bidders"
      val terminate = "'t': terminate auction and find the winner"
      val lines = st match {
        case Initial => Seq(inviteBidders, terminate)
        case BiddersInvited => Seq(terminate)
      }
      lines.foreach(println)
    }

    def loop(st: SellerState): Nothing = {
      options(st)
      readLine().strip() match {
        case "i" if st == Initial =>
          startAuction(auction)
          inviteBidders(auction)
          loop(BiddersInvited)
        case "t" =>
          terminateAuction(auction)
          sys.exit(0)
        case _ => loop(st)
      }
    }
    loop(Initial)
  }

  def acceptOrDeclineInvitation(invitation: AuctionInvitation): BuyerState = {
    println(s"Invitation received for '${invitation.auction.description}'. Do you accept it [y|n]?")
    readLine().strip match {
      case "y" => InvitationAccepted(invitation)
      case _ =>
        println("Dropping this invitation. Waiting for another one.")
        WaitingForInvitation
    }
  }

  def submitBid(invitation: AuctionInvitation, party: String): Unit = {
    println("Submitting a bid. Put your price [Decimal]:")
    Try(BigDecimal(readLine().strip())) match {
      case Success(decimal)=>
        client.getCommandClient().submitAndWait(
          "submitting-bid",
          appId,
          UUID.randomUUID().toString(),
          List(party,sellerParty).asJava,
          List(party,sellerParty).asJava,
          Collections.singletonList(
            invitation.createAndExerciseSubmitBid(
              new Offer(party, java.math.BigDecimal.valueOf(decimal.doubleValue))
            )
          ),
          sellerParty
        ).blockingGet()
        println("Bid submitted. Looping...")
        submitBid(invitation, party)
      case Failure(err) =>
        println(s"[ERR]: $err")
        submitBid(invitation, party)
    }
  }

  def runBuyer(party: String) = {
    def loop(buyerState: BuyerState): Unit = buyerState match {
      case WaitingForInvitation =>
        println(s"Waiting for the invitation to come...")
        val invitation = getLatestTransactions(party, AuctionInvitation.fromValue)
          .blockingFirst()
        loop(acceptOrDeclineInvitation(invitation))
      case state @ InvitationAccepted(invitation) =>
        submitBid(invitation, party)
        loop(state)
      case AuctionTerminated => ()
    }

    loop(WaitingForInvitation)
  }

  def getAllTransactions[A](party: String, f: Value => A): Flowable[A] =
    getTransactions(party, LedgerOffset.LedgerBegin.getInstance(), f)

  def getLatestTransactions[A](party: String, f: Value => A): Flowable[A] =
    getTransactions(party, LedgerOffset.LedgerEnd.getInstance, f)

  def getTransactions[A](party: String, lo: LedgerOffset, f: Value => A): Flowable[A] = {
    client.getTransactionsClient().getTransactions(
      lo,
      new FiltersByParty(Collections.singletonMap(party, NoFilter.instance)),
      true
    ).flatMap { tx =>
      Flowable.fromIterable {
        tx.getEvents().asScala
          .collect { case e: CreatedEvent => e.getArguments() }
          .map(record => Try(f(record)))
          .collect { case Success(value) => value }
          .asJava
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val userToParty = Map(aliceUser -> aliceParty, bobUser -> bobParty)
    args.toList match {
      case "-s" :: auctionDesc :: _ => runSeller(auctionDesc)
      case "-b" :: partyName :: _ if userToParty.contains(partyName) => runBuyer(userToParty.get(partyName).get)
      case _ =>
        println("failed...")
    }    
  }
}
