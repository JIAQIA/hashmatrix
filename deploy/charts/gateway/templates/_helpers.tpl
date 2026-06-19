{{- define "gateway.labels" -}}
app.kubernetes.io/part-of: gateway
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- if .Values.tenancy.brandingProfile }}
hashmatrix.io/branding-profile: {{ .Values.tenancy.brandingProfile }}
{{- end }}
{{- end -}}

{{- define "gateway.image" -}}
{{- $reg := .Values.global.imageRegistry | default "" -}}
{{- $img := printf "%s:%s" .Values.image.repository (.Values.image.tag | default .Chart.AppVersion) -}}
{{- if $reg }}{{ printf "%s/%s" $reg $img }}{{- else }}{{ $img }}{{- end }}
{{- end -}}
