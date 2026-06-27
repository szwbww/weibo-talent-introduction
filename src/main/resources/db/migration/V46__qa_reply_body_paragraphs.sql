-- Add paragraph breaks to long QA reply bodies for client reflow preservation.
-- ASCII-only literals; only reply_body is updated.

UPDATE qa_rule
   SET reply_body = 'Thank you for your interest in our talent program. This is a national-level initiative that connects outstanding overseas experts with Chinese enterprises through two main tracks: Innovative Talent Schemes for high-caliber researchers joining enterprises, and Entrepreneurial Talent Schemes for experts who can convert research into products.

Selected candidates may receive government research funding in the range of 3-12 million RMB, with enterprises providing personal salary support.

Typical application materials include a passport, doctoral degree certificate, CV with publications and achievements, proof of employment, and supporting certificates.

After you submit materials, our team matches partner enterprises, prepares application documents, and submits them for review; the overall cycle often spans six months or longer, with results commonly announced in late autumn.

We keep all materials strictly confidential, never charge fees, and you may take time to decide after selection.'
 WHERE reply_subject = 'Program overview';

UPDATE qa_rule
   SET reply_body = 'There are two projects: Innovative Talent Schemes and Entrepreneurial Talent Schemes. Innovative Talent Schemes are intended for individual talents who aim to join an enterprise with an exceptionally high salary.

Entrepreneurial Talent Schemes are designed for talents who can convert ideas into useful products.

A substantial amount of funding is allocated to this program.'
 WHERE reply_subject = 'About the talent program';

UPDATE qa_rule
   SET reply_body = 'Applicants should hold the title of associate professor or above, have outstanding research achievements in their field, and be able to contribute to industrial services and scientific and technological innovation.

Complete application materials are also required, including a passport, doctoral degree certificate, CV, proof of employment, and publication list.'
 WHERE reply_subject = 'Application criteria';

UPDATE qa_rule
   SET reply_body = 'First, you submit the required materials.

Then, our PhD team matches relevant enterprises according to your research direction and prepares the application documents.

Finally, the materials are submitted for review. The whole process usually takes about half a year or longer.'
 WHERE reply_subject = 'Application process';

UPDATE qa_rule
   SET reply_body = 'The applicant may negotiate with the company and work in a part-time capacity as a technical consultant, provide remote technical guidance, and visit China when necessary.

Related travel expenses can be covered according to the project arrangement.'
 WHERE reply_subject = 'Full-time and part-time options';

UPDATE qa_rule
   SET reply_body = 'You may decide whether to come to China, and you may take up to two years to consider the commitment.

It is also possible to visit China several times a year for technical exchanges, with related travel expenses covered according to the project arrangement.'
 WHERE reply_subject = 'Workplace arrangement';

UPDATE qa_rule
   SET reply_body = 'After a successful application, personal subsidies and research funding may be available.

If you are willing to establish a technology company in China, further support may also be provided for start-up capital or subsequent project funding.'
 WHERE reply_subject = 'Funding support';

UPDATE qa_rule
   SET reply_body = 'Thank you very much for your reply. I apologize for any inconvenience this message may have caused.

I hope you are enjoying a pleasant and fulfilling retirement, and I wish you all the best.

If it is convenient for you, we would greatly appreciate it if you could refer this project to anyone you believe might be a suitable candidate.'
 WHERE reply_subject = 'Thank you for your reply';
