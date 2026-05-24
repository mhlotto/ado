WITH daily_project AS (
  INSERT INTO projects (name, description, tags, is_core, core_key)
  VALUES ('Daily', '', '{}', TRUE, 'daily')
  ON CONFLICT (core_key) DO UPDATE
    SET is_core = TRUE, updated_at = now()
  RETURNING id
),
home_project AS (
  INSERT INTO projects (name, description, tags, is_core, core_key)
  VALUES ('Home', '', '{}', TRUE, 'home')
  ON CONFLICT (core_key) DO UPDATE
    SET is_core = TRUE, updated_at = now()
  RETURNING id
),
daily_template AS (
  INSERT INTO task_templates (project_id, template_key, name, description, is_system)
  SELECT id, 'daily', 'Daily', '', TRUE FROM daily_project
  ON CONFLICT (template_key) DO UPDATE
    SET project_id = EXCLUDED.project_id, name = EXCLUDED.name, updated_at = now()
  RETURNING id
),
summer_template AS (
  INSERT INTO task_templates (project_id, template_key, name, description, is_system)
  SELECT id, 'summer_chores', 'Summer chores', '', TRUE FROM home_project
  ON CONFLICT (template_key) DO UPDATE
    SET project_id = EXCLUDED.project_id, name = EXCLUDED.name, updated_at = now()
  RETURNING id
),
fall_template AS (
  INSERT INTO task_templates (project_id, template_key, name, description, is_system)
  SELECT id, 'fall_chores', 'Fall chores', '', TRUE FROM home_project
  ON CONFLICT (template_key) DO UPDATE
    SET project_id = EXCLUDED.project_id, name = EXCLUDED.name, updated_at = now()
  RETURNING id
),
winter_template AS (
  INSERT INTO task_templates (project_id, template_key, name, description, is_system)
  SELECT id, 'winter_chores', 'Winter chores', '', TRUE FROM home_project
  ON CONFLICT (template_key) DO UPDATE
    SET project_id = EXCLUDED.project_id, name = EXCLUDED.name, updated_at = now()
  RETURNING id
),
spring_template AS (
  INSERT INTO task_templates (project_id, template_key, name, description, is_system)
  SELECT id, 'spring_chores', 'Spring chores', '', TRUE FROM home_project
  ON CONFLICT (template_key) DO UPDATE
    SET project_id = EXCLUDED.project_id, name = EXCLUDED.name, updated_at = now()
  RETURNING id
)
INSERT INTO task_template_items (template_id, name, description, position)
SELECT id, item_name, '', position
FROM daily_template
CROSS JOIN (VALUES ('review calendar', 0), ('set priorities', 1)) AS items(item_name, position)
WHERE NOT EXISTS (SELECT 1 FROM task_template_items WHERE template_id = daily_template.id)
UNION ALL
SELECT id, 'seasonal home check', '', 0 FROM summer_template WHERE NOT EXISTS (SELECT 1 FROM task_template_items WHERE template_id = summer_template.id)
UNION ALL
SELECT id, 'seasonal home check', '', 0 FROM fall_template WHERE NOT EXISTS (SELECT 1 FROM task_template_items WHERE template_id = fall_template.id)
UNION ALL
SELECT id, 'seasonal home check', '', 0 FROM winter_template WHERE NOT EXISTS (SELECT 1 FROM task_template_items WHERE template_id = winter_template.id)
UNION ALL
SELECT id, 'seasonal home check', '', 0 FROM spring_template WHERE NOT EXISTS (SELECT 1 FROM task_template_items WHERE template_id = spring_template.id);
