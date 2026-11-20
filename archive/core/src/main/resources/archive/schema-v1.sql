-- Hämtat ordagrant ur docs/baseline/v0.1/04-datamodell.md, avsnitt 1.5 och 1.6 (baslinje v0.1).
-- Används i test mot vanlig SQLite (A-13, delvis). Room eller SQLDelight är inte valt.

CREATE TABLE archive_entry (
  id              TEXT PRIMARY KEY,          -- stabilt, t.ex. 'P-001', 'BS-03', 'V-012'
  origin_class    TEXT NOT NULL CHECK (origin_class IN
                    ('ORIGINAL_VERBATIM','APPROVED_EDIT','DERIVED_RULE','SYNTHETIC_SATIRE')),
  subtype         TEXT,                      -- 'normalisering', 'mönsterhypotes' m.fl.
  kind            TEXT NOT NULL,             -- 'quote','rule','lexicon','example','nav_line','tail_line'
  text            TEXT NOT NULL,
  text_sha256     TEXT NOT NULL,             -- räknas vid import, kontrolleras vid varje start
  language        TEXT NOT NULL DEFAULT 'sv',
  verification    TEXT NOT NULL CHECK (verification IN
                    ('VERIFIED_SOURCE','OWNER_CONFIRMED','DOCUMENTED_UNVERIFIED','RECALLED','UNKNOWN')),
  approval        TEXT NOT NULL CHECK (approval IN ('APPROVED','PENDING','REJECTED','BLOCKED')),
  visibility      TEXT NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE','COMPANY','OPEN')),
  author          TEXT NOT NULL,             -- 'Jimmy Bruce', 'Grok Bot (förslag)', 'okänd'
  source_ref      TEXT,                      -- t.ex. 'docs/baseline/source/Citatarkiv…txt#P-001'
  source_note     TEXT,
  date_known      TEXT,                      -- ISO-datum om känt, annars NULL
  derived_from    TEXT REFERENCES archive_entry(id),
  legacy_id       TEXT,
  imported_at     TEXT NOT NULL,
  revision        INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE archive_revision (              -- endast tillägg
  entry_id     TEXT NOT NULL REFERENCES archive_entry(id),
  revision     INTEGER NOT NULL,
  changed_at   TEXT NOT NULL,
  changed_by   TEXT NOT NULL,
  field        TEXT NOT NULL,
  old_value    TEXT,
  new_value    TEXT,
  reason       TEXT NOT NULL,
  PRIMARY KEY (entry_id, revision, field)
);

-- 1. Ett original får aldrig ändras i text, klass, författare eller källa.
CREATE TRIGGER original_immutable
BEFORE UPDATE ON archive_entry
WHEN OLD.origin_class = 'ORIGINAL_VERBATIM'
 AND (NEW.text IS NOT OLD.text OR NEW.text_sha256 IS NOT OLD.text_sha256
      OR NEW.origin_class IS NOT OLD.origin_class OR NEW.author IS NOT OLD.author
      OR NEW.source_ref IS NOT OLD.source_ref OR NEW.id IS NOT OLD.id)
BEGIN
  SELECT RAISE(ABORT, 'ORIGINAL_VERBATIM är skrivskyddad');
END;

-- 2. Ett original får aldrig raderas.
CREATE TRIGGER original_undeletable
BEFORE DELETE ON archive_entry
WHEN OLD.origin_class = 'ORIGINAL_VERBATIM'
BEGIN
  SELECT RAISE(ABORT, 'ORIGINAL_VERBATIM kan inte raderas');
END;

-- 3. Ingen post får uppgraderas till original efter import.
CREATE TRIGGER no_promotion_to_original
BEFORE UPDATE OF origin_class ON archive_entry
WHEN NEW.origin_class = 'ORIGINAL_VERBATIM' AND OLD.origin_class <> 'ORIGINAL_VERBATIM'
BEGIN
  SELECT RAISE(ABORT, 'Endast import från källa kan skapa ORIGINAL_VERBATIM');
END;

-- 4. Revisionshistoriken är bara tillägg.
CREATE TRIGGER revision_append_only_u BEFORE UPDATE ON archive_revision
BEGIN SELECT RAISE(ABORT, 'archive_revision är bara tillägg'); END;
CREATE TRIGGER revision_append_only_d BEFORE DELETE ON archive_revision
BEGIN SELECT RAISE(ABORT, 'archive_revision är bara tillägg'); END;

-- 5. Varje tillåten ändring loggas automatiskt.
CREATE TRIGGER log_visibility_change
AFTER UPDATE OF visibility ON archive_entry
BEGIN
  INSERT INTO archive_revision VALUES
    (NEW.id, OLD.revision + 1, strftime('%Y-%m-%dT%H:%M:%fZ','now'), 'app', 'visibility',
     OLD.visibility, NEW.visibility, 'användarval');
  UPDATE archive_entry SET revision = OLD.revision + 1 WHERE id = NEW.id;
END;
