-- ============================================================
-- Migracion: identificadores de mensajes y geolocalizacion
-- Objetivo: hydra-crud.onrender.com y geolocalizaci-n-1.onrender.com
-- Ejecutar en el SQL Editor de Supabase (proyecto mpmmdqnwsnjuzbsyigvq)
--
-- Diseño: el RUN NO se cifra y NO se agrega ninguna columna.
-- remitente_run / destinatario_run / paciente_run_p / run_p guardan
-- directamente el SHA-256 del RUN. No existen (ni se crean) *_run_hash.
--
-- Estado verificado (2026-10-02):
--   mensajes                  0/16 hasheadas  (RUN texto plano, 12 chars)
--   geolocalizacion           0/1  hasheadas  (RUN texto plano)
--   historial_geolocalizacion 0/90 hasheadas  (RUN texto plano)
--   bpm.paciente_run_p        ciphertext AES-GCM de 96 hex -> FUERA de este
--   script, depende del firmware del ESP32 (ver SQL_BPM_UPSERT.sql).
--
-- Cada UPDATE lleva doble filtro:
--   !~ '^[0-9a-f]{64}$'  -> idempotente: una fila ya hasheada no se re-hashea
--   ~  '^[0-9]...$'      -> guarda de forma: solo toca RUN, nunca ciphertext
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

-- ------------------------------------------------------------
-- 1) Permitir mensajes solo-foto (sin texto)
--    El cliente puede enviar una imagen sin escribir nada.
-- ------------------------------------------------------------
ALTER TABLE public.mensajes
    ALTER COLUMN contenido DROP NOT NULL;

-- ------------------------------------------------------------
-- 2) Backfill de mensajes
--    La expresion replica exactamente HashUtils.HASHEO() de Java:
--      - quita '.' y ' '
--      - pasa a minusculas
--      - SHA-256 en hexadecimal
--    Debe coincidir byte a byte con GET /api/user/cripto/hash.
-- ------------------------------------------------------------
UPDATE public.mensajes
   SET remitente_run = encode(
           digest(
               replace(replace(lower(btrim(remitente_run)), '.', ''), ' ', ''),
               'sha256'
           ),
           'hex'
       )
 WHERE remitente_run IS NOT NULL
   AND remitente_run !~ '^[0-9a-f]{64}$'
   AND remitente_run ~ '^[0-9]{1,2}\.?[0-9]{3}\.?[0-9]{3}-?[0-9Kk]$';

UPDATE public.mensajes
   SET destinatario_run = encode(
           digest(
               replace(replace(lower(btrim(destinatario_run)), '.', ''), ' ', ''),
               'sha256'
           ),
           'hex'
       )
 WHERE destinatario_run IS NOT NULL
   AND destinatario_run !~ '^[0-9a-f]{64}$'
   AND destinatario_run ~ '^[0-9]{1,2}\.?[0-9]{3}\.?[0-9]{3}-?[0-9Kk]$';

-- ------------------------------------------------------------
-- 3) Backfill de geolocalizacion (misma expresion)
--    UbicacionService.normalizar() solo acepta hash de 64 hex; con el RUN en
--    texto plano, cada GET devuelve 404.
-- ------------------------------------------------------------
UPDATE public.geolocalizacion
   SET paciente_run_p = encode(
           digest(
               replace(replace(lower(btrim(paciente_run_p)), '.', ''), ' ', ''),
               'sha256'
           ),
           'hex'
       )
 WHERE paciente_run_p IS NOT NULL
   AND paciente_run_p !~ '^[0-9a-f]{64}$'
   AND paciente_run_p ~ '^[0-9]{1,2}\.?[0-9]{3}\.?[0-9]{3}-?[0-9Kk]$';

UPDATE public.historial_geolocalizacion
   SET run_p = encode(
           digest(
               replace(replace(lower(btrim(run_p)), '.', ''), ' ', ''),
               'sha256'
           ),
           'hex'
       )
 WHERE run_p IS NOT NULL
   AND run_p !~ '^[0-9a-f]{64}$'
   AND run_p ~ '^[0-9]{1,2}\.?[0-9]{3}\.?[0-9]{3}-?[0-9Kk]$';

-- ------------------------------------------------------------
-- 4) Indices de soporte
--
--    NO se crean indices sueltos por columna:
--      idx_msg_conversa (remitente_run, destinatario_run, creado_en DESC)
--    ya cubre remitente_run y las conversaciones, asi que
--    idx_mensajes_remitente_run / idx_mensajes_destinatario_run serian
--    redundantes.
--
--    El badge de no leidos ya usa idx_msg_no_leidos
--    (destinatario_run, rol_destino) WHERE leido = false. Este indice agrega
--    creado_en para evitar el sort del ORDER BY.
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_mensajes_no_leidos_creado
    ON public.mensajes (destinatario_run, creado_en DESC)
    WHERE leido = false;

COMMIT;

-- ------------------------------------------------------------
-- 5) Verificacion (todos deben devolver 0)
-- ------------------------------------------------------------
-- SELECT COUNT(*) FROM public.mensajes
--  WHERE remitente_run !~ '^[0-9a-f]{64}$'
--     OR destinatario_run !~ '^[0-9a-f]{64}$';
--
-- SELECT COUNT(*) FROM public.geolocalizacion
--  WHERE paciente_run_p !~ '^[0-9a-f]{64}$';
--
-- SELECT COUNT(*) FROM public.historial_geolocalizacion
--  WHERE run_p !~ '^[0-9a-f]{64}$';
--
-- SELECT COUNT(*) FROM public.mensajes WHERE contenido IS NULL;

-- ============================================================
-- PENDIENTE FUERA DE ESTE SCRIPT
-- ============================================================
-- * El bucket de Supabase Storage "Mensajes" debe ser PRIVADO y los
--   adjuntos servirse con URL firmada. Nunca insertar una URL publica.
-- * Rotar la clave de la BD de Supabase en el dashboard: estaba en el
--   historial de git y en el codigo antes del commit 917d1bf.
-- * hydra_arm_security todavia expone /api/user/cripto/{cifrar,hash} y
--   usa app.crypto.*. Ese servicio no se modifica en esta migracion.
-- ============================================================
