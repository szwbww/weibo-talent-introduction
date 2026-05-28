INSERT INTO mail_template (
    template_code,
    template_name,
    subject,
    body,
    language
) VALUES
(
    'INTRODUCTION',
    'Project Introduction Email',
    'Research Collaboration Opportunity',
    'Dear Professor,

I hope this message finds you well. I am writing to inquire about the possibility of collaborating with you on a research project related to the problems faced by enterprises. We are keen on establishing a network of professors and researchers who are knowledgeable in various fields of science and technology, and who can contribute to addressing these problems. Given your expertise and experience, we believe that you may be a suitable candidate for this initiative.

However, in order to confirm your eligibility for the program, we kindly request that you share your CV with us at your earliest convenience. We appreciate your consideration and look forward to hearing back from you. Should you have any further questions or concerns, please do not hesitate to reach out to us. Thank you for your time.

This is my e-mail: ${senderEmail}

Sincerely,
${senderName}, ${senderTitle} ${teamName} ${countryName}',
    'en'
),
(
    'MEETING_INVITATION',
    'Meeting Invitation Email',
    'Follow-up on the Talent Program',
    'Good day from ${senderDisplayName},

This is a warm follow-up message.

I know your time is valuable, and I truly appreciate your consideration. If the opportunity still aligns with your interests, I would be delighted to explore it further with you.

If now is not the right moment, I completely understand. Please feel free to let me know what timing works best for you.

My working hours are between 9:00 a.m. and 5:00 p.m. Please feel free to share your available time slots, and I would be happy to provide further clarification about this program in an online meeting.

Looking forward to hearing from you.',
    'en'
);
