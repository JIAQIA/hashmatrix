{{/*
platform umbrella —— 公共模板 helpers。
*/}}

{{/* chart 名称（可被 nameOverride 覆盖）。 */}}
{{- define "platform.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* 公共标签（所有本 chart 自有清单复用）。 */}}
{{- define "platform.labels" -}}
app.kubernetes.io/name: {{ include "platform.name" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: hashmatrix
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
hashmatrix.io/env: {{ .Values.global.env | default "base" | quote }}
{{- end -}}
