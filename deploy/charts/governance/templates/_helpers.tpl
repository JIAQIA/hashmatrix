{{- define "governance.labels" -}}
app.kubernetes.io/part-of: platform
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "governance.image" -}}
{{- $reg := .Values.global.imageRegistry | default "" -}}
{{- $img := printf "%s:%s" .Values.image.repository (.Values.image.tag | default .Chart.AppVersion) -}}
{{- if $reg }}{{ printf "%s/%s" $reg $img }}{{- else }}{{ $img }}{{- end }}
{{- end -}}
