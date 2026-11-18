import { useEffect, useState } from 'react'
import { CircleMarker, MapContainer, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'

export interface LatLng {
  lat: number
  lng: number
}

// Chua chon diem nao thi mo ban do o trung tam Quan 1, TP.HCM
const DEFAULT_CENTER: LatLng = { lat: 10.7769, lng: 106.7009 }

interface Props {
  value: LatLng | null
  onChange: (point: LatLng) => void
}

// Bam vao ban do thi bao toa do diem vua bam cho component cha
function ClickToPick({ onChange }: { onChange: (point: LatLng) => void }) {
  useMapEvents({
    click(e) {
      onChange({ lat: e.latlng.lat, lng: e.latlng.lng })
    },
  })
  return null
}

// Diem duoc chon thay doi (bam ban do, hoac nut "Dung vi tri hien tai") thi dua ban do toi diem do.
// useEffect o day la de dong bo voi thu vien ban do (he thong ben ngoai React), khong phai de tai du lieu.
function FollowPoint({ point }: { point: LatLng | null }) {
  const map = useMap()
  useEffect(() => {
    if (point) {
      map.setView([point.lat, point.lng], Math.max(map.getZoom(), 16))
    }
  }, [map, point])
  return null
}

/**
 * Ban do OpenStreetMap (thu vien Leaflet) de chon toa do giao hang: bam vao ban do de dat diem,
 * hoac lay vi tri hien tai tu trinh duyet. Diem duoc ve bang CircleMarker (cham tron vector)
 * nen khong can file anh icon cua Leaflet.
 */
export default function LocationPicker({ value, onChange }: Props) {
  const [locating, setLocating] = useState(false)
  const [locateError, setLocateError] = useState<string | null>(null)
  const center = value ?? DEFAULT_CENTER

  function locateMe() {
    if (!navigator.geolocation) {
      setLocateError('Trình duyệt không hỗ trợ lấy vị trí')
      return
    }
    setLocating(true)
    setLocateError(null)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        onChange({ lat: position.coords.latitude, lng: position.coords.longitude })
        setLocating(false)
      },
      () => {
        setLocateError('Không lấy được vị trí, hãy cho phép truy cập vị trí hoặc bấm chọn trên bản đồ')
        setLocating(false)
      },
    )
  }

  return (
    <div>
      <div className="h-72 overflow-hidden rounded-lg border">
        <MapContainer center={[center.lat, center.lng]} zoom={value ? 16 : 13} className="h-full w-full">
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <ClickToPick onChange={onChange} />
          <FollowPoint point={value} />
          {value && (
            <CircleMarker
              center={[value.lat, value.lng]}
              radius={10}
              pathOptions={{ color: '#ea580c', fillColor: '#f97316', fillOpacity: 0.8 }}
            />
          )}
        </MapContainer>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-3 text-sm">
        <button
          type="button"
          onClick={locateMe}
          disabled={locating}
          className="rounded border px-3 py-1 hover:bg-gray-100 disabled:opacity-50"
        >
          {locating ? 'Đang lấy vị trí...' : '📍 Dùng vị trí hiện tại'}
        </button>
        <span className="text-gray-500">
          {value ? `Đã chọn: ${value.lat.toFixed(5)}, ${value.lng.toFixed(5)}` : 'Bấm vào bản đồ để chọn điểm giao'}
        </span>
      </div>
      {locateError && <p className="mt-1 text-sm text-red-600">{locateError}</p>}
    </div>
  )
}
