{{/*
tenant chart —— 公共 helpers。
*/}}

{{/* 租户 id（必填，渲染期校验非空）。 */}}
{{- define "tenant.id" -}}
{{- $id := .Values.tenant.id | toString -}}
{{- if not $id }}{{ fail "tenant.id 必填：请在 values-tenant-<id>.yaml 指定 tenant.id" }}{{- end -}}
{{- $id -}}
{{- end -}}

{{/* 租户命名空间（默认 tenant-<id>）。 */}}
{{- define "tenant.namespace" -}}
{{- default (printf "tenant-%s" (include "tenant.id" .)) .Values.tenant.namespace -}}
{{- end -}}

{{/* PG schema（默认 tenant_<id>）。 */}}
{{- define "tenant.pgSchema" -}}
{{- default (printf "tenant_%s" (include "tenant.id" .)) .Values.data.postgres.schema -}}
{{- end -}}

{{/* PG database（默认 tenant_<id>）。 */}}
{{- define "tenant.pgDatabase" -}}
{{- default (printf "tenant_%s" (include "tenant.id" .)) .Values.data.postgres.database -}}
{{- end -}}

{{/* Doris catalog（默认 catalog_<id>）。 */}}
{{- define "tenant.dorisCatalog" -}}
{{- default (printf "catalog_%s" (include "tenant.id" .)) .Values.data.doris.catalog -}}
{{- end -}}

{{/* 公共标签。 */}}
{{- define "tenant.labels" -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: hashmatrix
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
hashmatrix.io/tenant: {{ include "tenant.id" . | quote }}
{{- end -}}
