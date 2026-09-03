UPDATE mail_sender_account
   SET auto_send_paused = 0,
       auto_send_paused_reason = NULL,
       auto_send_paused_at = NULL
 WHERE auto_send_paused = 1
   AND auto_send_paused_reason LIKE 'BOUNCE_RATE_HIGH:%';
