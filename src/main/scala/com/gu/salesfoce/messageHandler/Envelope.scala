package com.gu.salesfoce.messageHandler

case class Envelope(body: Body)

case class Body(notifications: List[NotificationMessage])

case class NotificationMessage(
  OrganizationId: String,
  ActionId: String,
  SessionId: String,
  EnterpriseUrl: String,
  PartnerUrl: String,
  notification: Notification)

case class Notification(Id: String, Contact: Contact1)

case class Contact1(
  Id: String,
  Delivery_Information__c: String,
  FirstName: String,
  LastName: String,
  MailingStreet: String,
  OtherStreet: String)