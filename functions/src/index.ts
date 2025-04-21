// Use V2 specific imports
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger"; // Use V2 logger
import * as admin from "firebase-admin";

// Initialize Firebase Admin SDK ONLY ONCE
admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

// V2 Function Definition using onDocumentCreated
export const notifyOnNewMessage = onDocumentCreated("messages/{messageId}", async (event) => {
    // Get the snapshot and check if it exists
    const snapshot = event.data;
    if (!snapshot) {
      logger.log("No data associated with the event", event.params.messageId);
      return;
    }

    const messageData = snapshot.data();
    if (!messageData) {
      logger.log("No message data found for messageId:", event.params.messageId);
      return;
    }

    // Access wildcard parameters via event.params
    const messageId = event.params.messageId;
    const senderId = messageData.senderId;
    const receiverId = messageData.receiverId;
    const messageText = messageData.text ?? "Sent an attachment"; // Handle non-text messages
    const conversationId = messageData.conversationId;

    // Basic validation
    if (!senderId || !receiverId || !conversationId) {
         logger.error("Missing senderId, receiverId, or conversationId in message:", messageId, messageData);
         return;
    }

    logger.log(
      `New message detected: ${messageId}. From: ${senderId}, To: ${receiverId}, ConvID: ${conversationId}`
    );

    // 1. Avoid sending notification back to sender
    if (senderId === receiverId) {
       logger.log("Sender and receiver are the same. No notification needed.");
       return;
    }

    // 2. --- Get Receiver's FCM Token and Role ---
    let receiverToken: string | null = null;
    let receiverRole = "client"; // Assume client by default
    let receiverDocRef: admin.firestore.DocumentReference | null = null;

    try {
        // Try fetching from 'users' (clients) first
        const userDocRef = db.collection("users").doc(receiverId);
        const userDoc = await userDocRef.get();
        if (userDoc.exists && userDoc.data()?.fcmToken) {
            receiverToken = userDoc.data()?.fcmToken;
            receiverRole = "client";
            receiverDocRef = userDocRef; // Store ref for potential token removal
            logger.log(`Receiver ${receiverId} found in 'users'. Role: ${receiverRole}, Token: ${receiverToken ? 'Exists' : 'MISSING'}`);
        } else {
             logger.log(`Receiver ${receiverId} not in 'users' or no token. Trying 'lawyers'.`);
             // If not found in users, try lawyers
             const lawyerDocRef = db.collection("lawyers").doc(receiverId);
             const lawyerDoc = await lawyerDocRef.get();
             if (lawyerDoc.exists && lawyerDoc.data()?.fcmToken) {
                 receiverToken = lawyerDoc.data()?.fcmToken;
                 receiverRole = "lawyer";
                 receiverDocRef = lawyerDocRef; // Store ref
                 logger.log(`Receiver ${receiverId} found in 'lawyers'. Role: ${receiverRole}, Token: ${receiverToken ? 'Exists' : 'MISSING'}`);
             } else {
                  logger.log(`Receiver ${receiverId} not found in 'lawyers' or no token.`);
             }
        }
    } catch (error) {
       logger.error(`Error fetching receiver's (${receiverId}) token:`, error);
       return; // Exit if we can't fetch token info
    }

    if (!receiverToken) {
      logger.log(`No FCM token found for receiver: ${receiverId}. Cannot send notification.`);
      return;
    }


    // 3. --- Get Sender's Name ---
    let senderName: string = "Someone"; // Default sender name
    try {
         // Try fetching from 'users' (clients) first
         const senderUserDoc = await db.collection("users").doc(senderId).get();
         if (senderUserDoc.exists) {
             const data = senderUserDoc.data();
             senderName = `${data?.firstName ?? ""} ${data?.lastName ?? ""}`.trim() || "Client User";
             logger.log(`Sender ${senderId} name '${senderName}' found in 'users'.`);
         } else {
              // If not found in users, try lawyers
              const senderLawyerDoc = await db.collection("lawyers").doc(senderId).get();
              if (senderLawyerDoc.exists) {
                  const data = senderLawyerDoc.data();
                  senderName = `Atty. ${data?.firstName ?? ""} ${data?.lastName ?? ""}`.trim() || "Lawyer User";
                  logger.log(`Sender ${senderId} name '${senderName}' found in 'lawyers'.`);
              } else {
                   logger.log(`Sender ${senderId} not found in 'users' or 'lawyers'. Using default name.`);
                   senderName = "Unknown User"; // Fallback if sender not found
              }
         }
    } catch (error) {
        logger.error(`Error fetching sender's (${senderId}) name:`, error);
        senderName = "Someone"; // Fallback on error
    }


    // 4. --- Construct Notification Payload ---
    const payload: admin.messaging.MessagingPayload = {
      notification: {
        title: senderName,
        body: messageText,
      },
      data: {
        title: senderName,
        body: messageText,
        senderId: senderId,
        receiverId: receiverId,
        conversationId: conversationId,
        senderName: senderName,
        receiverRole: receiverRole,
        click_action: "FLUTTER_NOTIFICATION_CLICK", // Keep or remove based on testing
      },
    };

    const options: admin.messaging.MessagingOptions = {
      priority: "high",
      timeToLive: 60 * 60 * 24 * 7, // 1 week
    };

    // 5. --- Send the Notification ---
    // Use non-null assertion (!) because we checked receiverToken earlier
    logger.log(`Attempting to send notification for message ${messageId} to receiver ${receiverId} (Token: ${receiverToken!.substring(0, 10)}...)`);
    try {
       const response = await messaging.sendToDevice(receiverToken, payload, options);
       logger.log(`Successfully sent message ${messageId} notification:`, JSON.stringify(response));

       // 6. --- Handle Potential Invalid Tokens ---
       if (response.failureCount > 0) {
           response.results.forEach(async (result) => {
                const error = result.error;
                if (error) {
                    // Fix null check for logging: Use assertion as receiverToken guaranteed non-null here
                    logger.error(
                        `Failure sending notification for message ${messageId} to token ${receiverToken!.substring(0,10)}... Error:`,
                        error.code, error.message
                    );
                    if (error.code === "messaging/invalid-registration-token" ||
                        error.code === "messaging/registration-token-not-registered") {

                        logger.log(`Invalid token detected for receiver ${receiverId}. Attempting to remove from Firestore.`);
                        if (receiverDocRef) {
                             try {
                                 await receiverDocRef.update({ fcmToken: admin.firestore.FieldValue.delete() });
                                 logger.log(`Successfully removed invalid token for user ${receiverId} in collection ${receiverDocRef.parent.id}`);
                             } catch (removeError) {
                                 logger.error(`Failed to remove invalid token for user ${receiverId}:`, removeError);
                             }
                        } else {
                             logger.error(`Cannot remove token for ${receiverId}: Document reference was not determined.`);
                        }
                    }
                }
           });
       }
       return response;
    } catch (error) {
      logger.error(`Error sending FCM message for message ${messageId}:`, error);
      return null;
    }
});