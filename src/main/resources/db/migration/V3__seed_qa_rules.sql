INSERT INTO qa_category (id, category_code, category_name, description) VALUES
(1, 'PROJECT_CONTENT', 'Project content', 'Overview of the available talent schemes'),
(2, 'ENTRY_FORMAT', 'Entry format', 'Individual or joint application options'),
(3, 'APPLYING_CRITERIA', 'Applying criteria', 'Eligibility and required materials'),
(4, 'ROLE', 'Role', 'Possible roles after joining the program'),
(5, 'DUTY_AND_RIGHT', 'Duty and right', 'Responsibilities and benefits'),
(6, 'FULL_TIME_PART_TIME', 'Full-time / part-time', 'Whether the role must be full-time'),
(7, 'WORKPLACE', 'Workplace', 'Whether the applicant must work in China'),
(8, 'SALARY', 'Salary', 'Subsidies and research funding'),
(9, 'PROJECT_STREAM', 'Project stream', 'Application process'),
(10, 'DEADLINE', 'Deadline', 'Submission deadline'),
(11, 'OUR_ADVANTAGE', 'Our advantage', 'Team experience and support'),
(12, 'RETIRED', 'Retired', 'Polite response for retired experts');

INSERT INTO qa_rule (
    category_id,
    keywords,
    match_mode,
    priority,
    reply_subject,
    reply_body,
    auto_reply_enabled,
    handoff_required
) VALUES
(1, 'project,program,scheme,what is this project', 'ANY', 10, 'About the talent program', 'There are two projects: Innovative Talent Schemes and Entrepreneurial Talent Schemes. Innovative Talent Schemes are intended for individual talents who aim to join an enterprise with an exceptionally high salary. Entrepreneurial Talent Schemes are designed for talents who can convert ideas into useful products. A substantial amount of funding is allocated to this program.', 1, 0),
(2, 'apply individually,apply jointly,team,partner', 'ANY', 20, 'Application format', 'You may apply individually or jointly. You may also join the project with research partners and participate as a team to start a business in China.', 1, 0),
(3, 'criteria,qualification,eligible,requirements', 'ANY', 30, 'Application criteria', 'Applicants should hold the title of associate professor or above, have outstanding research achievements in their field, and be able to contribute to industrial services and scientific and technological innovation. Complete application materials are also required, including a passport, doctoral degree certificate, CV, proof of employment, and publication list.', 1, 0),
(4, 'role,position,what would i do', 'ANY', 40, 'Possible role', 'You may work as a researcher in a company related to your field, or you may establish your own company in cooperation with a Chinese company.', 1, 0),
(5, 'duty,right,responsibility,benefit', 'ANY', 50, 'Responsibilities and benefits', 'You may use your expertise to support a company or start a business. You may receive salary support and other coordinated assistance according to the project arrangement.', 1, 0),
(6, 'full time,part time,remote,technical consultant', 'ANY', 60, 'Full-time and part-time options', 'The applicant may negotiate with the company and work in a part-time capacity as a technical consultant, provide remote technical guidance, and visit China when necessary. Related travel expenses can be covered according to the project arrangement.', 1, 0),
(7, 'workplace,china,relocate,come to china', 'ANY', 70, 'Workplace arrangement', 'You may decide whether to come to China, and you may take up to two years to consider the commitment. It is also possible to visit China several times a year for technical exchanges, with related travel expenses covered according to the project arrangement.', 1, 0),
(8, 'salary,subsidy,funding,compensation', 'ANY', 80, 'Funding support', 'After a successful application, personal subsidies and research funding may be available. If you are willing to establish a technology company in China, further support may also be provided for start-up capital or subsequent project funding.', 1, 0),
(9, 'process,procedure,application process,timeline', 'ANY', 90, 'Application process', 'First, you submit the required materials. Then, our PhD team matches relevant enterprises according to your research direction and prepares the application documents. Finally, the materials are submitted for review. The whole process usually takes about half a year or longer.', 1, 0),
(10, 'deadline,when to apply,submission deadline', 'ANY', 100, 'Application deadline', 'We are inviting outstanding experts and scholars to participate in the current project cycle. The final submission deadline should be confirmed according to the latest project notice before application.', 1, 0),
(11, 'why you,advantage,experience,support', 'ANY', 110, 'Our support', 'We have extensive experience in supporting experts and scholars from around the world. In addition to government subsidies, we can also provide financial support for suitable entrepreneurial projects.', 1, 0),
(12, 'retired,i am retired,no longer working', 'ANY', 5, 'Thank you for your reply', 'Thank you very much for your reply. I apologize for any inconvenience this message may have caused. I hope you are enjoying a pleasant and fulfilling retirement, and I wish you all the best. If it is convenient for you, we would greatly appreciate it if you could refer this project to anyone you believe might be a suitable candidate.', 1, 0);
