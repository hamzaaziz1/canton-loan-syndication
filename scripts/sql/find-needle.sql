-- Session-scoped search across every table in the public schema:
-- bytea columns for a byte needle, text columns for a text needle.
-- Created in pg_temp, so nothing is left behind in the node's database.
CREATE FUNCTION pg_temp.find_needle(needle bytea, needle_text text)
RETURNS TABLE (tbl text, col text, hits bigint)
LANGUAGE plpgsql AS $fn$
DECLARE
  r record;
  n bigint;
BEGIN
  FOR r IN
    SELECT c.table_name, c.column_name, c.data_type
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.data_type IN ('bytea', 'text', 'character varying')
  LOOP
    IF r.data_type = 'bytea' THEN
      EXECUTE format('SELECT count(*) FROM %I WHERE position($1 IN %I) > 0', r.table_name, r.column_name)
        INTO n USING needle;
    ELSE
      EXECUTE format('SELECT count(*) FROM %I WHERE position($1 IN %I) > 0', r.table_name, r.column_name)
        INTO n USING needle_text;
    END IF;
    IF n > 0 THEN
      tbl := r.table_name;
      col := r.column_name;
      hits := n;
      RETURN NEXT;
    END IF;
  END LOOP;
END
$fn$;
