-- Repair QA reply bodies that may have been persisted with mojibake range separators.
-- Keep literals ASCII-only to avoid client-encoding dependent punctuation.
UPDATE qa_rule
   SET reply_body = 'To prevent AI-forged materials and duplicate applications, we need a short confirmation video (about 3-7 minutes) showing you holding your passport and reading the commitment statement. Please submit it together with your application materials.'
 WHERE reply_subject = 'Confirmation video requirement';

UPDATE qa_rule
   SET reply_body = 'Zoom, Teams, or Webex are all fine. We typically schedule 15-20 minutes and will arrange a time based on your time zone, sending the meeting link before the call.'
 WHERE reply_subject = 'Meeting arrangement';

UPDATE qa_rule
   SET reply_body = 'Thank you for your interest in our talent program. This is a national-level initiative that connects outstanding overseas experts with Chinese enterprises through two main tracks: Innovative Talent Schemes for high-caliber researchers joining enterprises, and Entrepreneurial Talent Schemes for experts who can convert research into products. Selected candidates may receive government research funding in the range of 3-12 million RMB, with enterprises providing personal salary support. Typical application materials include a passport, doctoral degree certificate, CV with publications and achievements, proof of employment, and supporting certificates. After you submit materials, our team matches partner enterprises, prepares application documents, and submits them for review; the overall cycle often spans six months or longer, with results commonly announced in late autumn. We keep all materials strictly confidential, never charge fees, and you may take time to decide after selection.'
 WHERE reply_subject = 'Program overview';
