-- Generated columns keep old and new application writers compatible; existing records are backfilled by MySQL.
ALTER TABLE discovery_paper_archive_record
    ADD COLUMN doi_lookup_sha CHAR(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
      (CASE WHEN JSON_VALID(metadata_json) THEN SHA2(LOWER(REPLACE(REPLACE(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(metadata_json,'$.doi')),'https://doi.org/',''),'http://doi.org/',''),'doi:','')),256) END) VIRTUAL,
    ADD COLUMN pmid_lookup_sha CHAR(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
      (CASE WHEN JSON_VALID(metadata_json) THEN SHA2(JSON_UNQUOTE(JSON_EXTRACT(metadata_json,'$.pmid')),256) END) VIRTUAL,
    ADD COLUMN pmcid_lookup_sha CHAR(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
      (CASE WHEN JSON_VALID(metadata_json) THEN SHA2(UPPER(JSON_UNQUOTE(JSON_EXTRACT(metadata_json,'$.pmcId'))),256) END) VIRTUAL,
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE discovery_paper_archive_record
    ADD INDEX idx_paper_source_key (source_name, paper_key, id),
    ADD INDEX idx_paper_doi_lookup (doi_lookup_sha, id),
    ADD INDEX idx_paper_pmid_lookup (pmid_lookup_sha, id),
    ADD INDEX idx_paper_pmcid_lookup (pmcid_lookup_sha, id),
    ALGORITHM=INPLACE, LOCK=NONE;
